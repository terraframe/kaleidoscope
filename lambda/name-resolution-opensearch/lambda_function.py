import json
import os
from typing import Any, Dict, List, Optional

from boto3 import Session
from opensearchpy import OpenSearch, RequestsHttpConnection

try:
    # Native SigV4 authentication support in opensearch-py.
    from opensearchpy import AWSV4SignerAuth
except ImportError:
    AWSV4SignerAuth = None


def str_to_bool(
    value: Optional[str],
    default: bool = False,
) -> bool:
    if value is None:
        return default

    return value.strip().lower() in {
        "1",
        "true",
        "yes",
        "y",
        "on",
    }


def build_client() -> OpenSearch:
    host = os.environ["OPENSEARCH_HOST"]

    region = os.environ.get(
        "AWS_REGION",
        os.environ.get("OPENSEARCH_REGION", "us-west-2"),
    )

    port = int(os.environ.get("OPENSEARCH_PORT", "443"))

    use_ssl = str_to_bool(
        os.environ.get("OPENSEARCH_USE_SSL", "true"),
        True,
    )

    verify_certs = str_to_bool(
        os.environ.get("OPENSEARCH_VERIFY_CERTS", "true"),
        True,
    )

    use_iam = str_to_bool(
        os.environ.get("OPENSEARCH_USE_IAM", "true"),
        True,
    )

    http_auth = None

    if use_iam:
        if AWSV4SignerAuth is None:
            raise RuntimeError(
                "OPENSEARCH_USE_IAM=true but AWSV4SignerAuth "
                "is unavailable. Install a version of "
                "opensearch-py with SigV4 support."
            )

        credentials = Session().get_credentials()

        if credentials is None:
            raise RuntimeError(
                "Could not resolve AWS credentials for "
                "OpenSearch IAM authentication."
            )

        frozen_credentials = credentials.get_frozen_credentials()

        http_auth = AWSV4SignerAuth(
            frozen_credentials,
            region,
            "es",
        )

    else:
        username = os.environ.get("OPENSEARCH_USERNAME")
        password = os.environ.get("OPENSEARCH_PASSWORD")

        if username and password:
            http_auth = (username, password)

    return OpenSearch(
        hosts=[
            {
                "host": host,
                "port": port,
            }
        ],
        http_auth=http_auth,
        use_ssl=use_ssl,
        verify_certs=verify_certs,
        ssl_assert_hostname=verify_certs,
        ssl_show_warn=False,
        connection_class=RequestsHttpConnection,
        timeout=20,
        max_retries=3,
        retry_on_timeout=True,
    )


def normalize_query(value: Optional[str]) -> str:
    if value is None:
        return ""

    return value.strip()


def tokenize(value: Optional[str]) -> List[str]:
    normalized = normalize_query(value)

    if not normalized:
        return []

    return [
        part.strip()
        for part in normalized.split()
        if part.strip()
    ]


def add_keyword_term(
    clauses: List[Dict[str, Any]],
    field: str,
    value: str,
    boost: float,
) -> None:
    if not value:
        return

    clauses.append({
        "term": {
            field: {
                "value": value,
                "boost": boost,
            }
        }
    })


def build_query(name: str) -> Dict[str, Any]:
    """
    Search strategy:

    1. Exact whole-query matches get the largest boosts.
    2. Exact token matches rescue mixed queries such as:
         channel reach_25
    3. AND full-text matching provides a high-quality search path.
    4. OR and fuzzy matching prevent noisy words from eliminating results.

    Expected keyword fields:

      - code.keyword
      - altLabel.keyword
      - label.keyword
    """

    normalized_name = normalize_query(name)
    tokens = tokenize(normalized_name)

    should_clauses: List[Dict[str, Any]] = []

    # Whole-query exact matches.
    add_keyword_term(
        should_clauses,
        "code.keyword",
        normalized_name,
        1000,
    )

    add_keyword_term(
        should_clauses,
        "altLabel.keyword",
        normalized_name,
        1000,
    )

    add_keyword_term(
        should_clauses,
        "label.keyword",
        normalized_name,
        200,
    )

    # Per-token exact matches.
    for token in tokens:
        add_keyword_term(
            should_clauses,
            "code.keyword",
            token,
            700,
        )

        add_keyword_term(
            should_clauses,
            "altLabel.keyword",
            token,
            600,
        )

        add_keyword_term(
            should_clauses,
            "label.keyword",
            token,
            150,
        )

    # Phrase-oriented label searches.
    if normalized_name:
        should_clauses.append({
            "match_phrase": {
                "label": {
                    "query": normalized_name,
                    "boost": 50,
                }
            }
        })

        should_clauses.append({
            "match_phrase_prefix": {
                "label": {
                    "query": normalized_name,
                    "boost": 25,
                }
            }
        })

    # High-quality full-text path requiring all terms.
    if normalized_name:
        should_clauses.append({
            "multi_match": {
                "query": normalized_name,
                "fields": [
                    "label^5",
                    "altLabel^4",
                    "code^3",
                    "type",
                ],
                "type": "best_fields",
                "operator": "and",
                "boost": 10,
            }
        })

    # More forgiving full-text fallback.
    if normalized_name:
        should_clauses.append({
            "multi_match": {
                "query": normalized_name,
                "fields": [
                    "label^3",
                    "altLabel^3",
                    "code^2",
                    "type",
                ],
                "type": "best_fields",
                "operator": "or",
                "fuzziness": "AUTO",
                "minimum_should_match": "1",
                "boost": 1,
            }
        })

    # Per-token fuzzy text matches.
    for token in tokens:
        should_clauses.append({
            "multi_match": {
                "query": token,
                "fields": [
                    "label^3",
                    "altLabel^3",
                    "code^2",
                    "type",
                ],
                "type": "best_fields",
                "operator": "or",
                "fuzziness": "AUTO",
                "boost": 2,
            }
        })

    return {
        "size": 100,
        "_source": [
            "code",
            "type",
            "uri",
            "label",
            "altLabel",
        ],
        "query": {
            "bool": {
                "should": should_clauses,
                "minimum_should_match": 1,
            }
        },
        "sort": [
            "_score",
        ],
    }


def execute(name: str) -> Dict[str, Any]:
    try:
        index_name = os.environ["OPENSEARCH_INDEX"]

        client = build_client()
        query = build_query(name)

        print(
            "OPENSEARCH QUERY:",
            json.dumps(query, default=str),
        )

        response = client.search(
            index=index_name,
            body=query,
        )

        results: List[Dict[str, str]] = []

        hits = (
            response
            .get("hits", {})
            .get("hits", [])
        )

        for hit in hits:
            source = hit.get("_source", {})

            code = source.get("code")
            item_type = source.get("type")
            uri = source.get("uri")

            if not code or not item_type or not uri:
                continue

            results.append({
                "code": str(code),
                "type": str(item_type),
                "uri": str(uri),
            })

        return {
            "success": True,
            "query": name,
            "resultCount": len(results),
            "results": results,
        }

    except KeyError as error:
        return {
            "success": False,
            "error": (
                f"Missing required environment variable: "
                f"{error.args[0]}"
            ),
        }

    except Exception as error:
        print(
            "OPENSEARCH ERROR:",
            repr(error),
        )

        return {
            "success": False,
            "error": (
                "OpenSearch query failed: "
                f"{type(error).__name__}: {error}"
            ),
        }


def lambda_handler(
    event: Dict[str, Any],
    context: Any,
) -> Dict[str, Any]:
    print(
        "EVENT:",
        json.dumps(event, default=str),
    )

    if not isinstance(event, dict):
        return {
            "success": False,
            "error": "Invalid request: expected a JSON object.",
        }

    name = event.get("name")

    if not isinstance(name, str) or not name.strip():
        return {
            "success": False,
            "error": (
                "Missing or invalid required parameter: name"
            ),
        }

    return execute(name.strip())