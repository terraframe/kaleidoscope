import os
from typing import Any
from urllib.parse import urlencode

import requests
from botocore.auth import SigV4Auth
from botocore.awsrequest import AWSRequest
from botocore.session import Session


MAX_ERROR_LENGTH = 1000
DEFAULT_MAX_ROWS = 100
REQUEST_TIMEOUT_SECONDS = 240


def is_truthy(value: Any) -> bool:
    return str(value).strip().lower() in {
        "1",
        "true",
        "yes",
        "y",
        "on",
    }


def get_max_rows() -> int:
    raw_value = os.getenv("MAX_SPARQL_ROWS", str(DEFAULT_MAX_ROWS))

    try:
        return max(1, int(raw_value))
    except ValueError:
        return DEFAULT_MAX_ROWS


def execute(statement: str) -> dict[str, Any]:
    sparql_url = os.getenv("SPARQL_URL")
    use_iam = is_truthy(os.getenv("USE_IAM", "false"))
    aws_region = (
        os.getenv("AWS_REGION")
        or os.getenv("AWS_DEFAULT_REGION")
    )

    if not sparql_url:
        return {
            "success": False,
            "error": "SPARQL_URL is not set",
        }

    if use_iam and not aws_region:
        return {
            "success": False,
            "error": "USE_IAM is true but AWS_REGION is not set",
        }

    response: requests.Response | None = None

    try:
        # Construct the exact encoded body that will be signed and sent.
        body = urlencode({"query": statement})

        headers = {
            "Content-Type": "application/x-www-form-urlencoded",
            "Accept": "application/sparql-results+json",
        }

        if use_iam:
            session = Session()
            credentials = session.get_credentials()

            if credentials is None:
                return {
                    "success": False,
                    "error": (
                        "USE_IAM is true but no AWS credentials "
                        "were found"
                    ),
                }

            frozen_credentials = credentials.get_frozen_credentials()

            aws_request = AWSRequest(
                method="POST",
                url=sparql_url,
                data=body,
                headers=headers,
            )

            SigV4Auth(
                frozen_credentials,
                "neptune-db",
                aws_region,
            ).add_auth(aws_request)

            headers = dict(aws_request.headers.items())

        response = requests.post(
            sparql_url,
            data=body,
            headers=headers,
            timeout=REQUEST_TIMEOUT_SECONDS,
        )

        response.raise_for_status()

        try:
            json_response = response.json()
        except ValueError:
            return {
                "success": False,
                "error": (
                    "Received a non-JSON response from the "
                    "SPARQL endpoint"
                ),
                "details": response.text[:MAX_ERROR_LENGTH],
            }

        columns = json_response.get("head", {}).get("vars", [])
        bindings = (
            json_response
            .get("results", {})
            .get("bindings", [])
        )

        max_rows = get_max_rows()
        rows: list[dict[str, Any]] = []

        for binding in bindings[:max_rows]:
            row: dict[str, Any] = {}

            for column in columns:
                value = binding.get(column)

                if not value:
                    row[column] = None
                    continue

                row[column] = {
                    "value": value.get("value"),
                    "type": value.get("type"),
                }

                if "datatype" in value:
                    row[column]["datatype"] = value["datatype"]

                if "xml:lang" in value:
                    row[column]["language"] = value["xml:lang"]

            rows.append(row)

        return {
            "success": True,
            "columns": columns,
            "rows": rows,
            "rowCount": len(rows),
            "resultCountBeforeLimit": len(bindings),
            "truncated": len(bindings) > max_rows,
        }

    except requests.exceptions.HTTPError as error:
        details = (
            response.text[:MAX_ERROR_LENGTH]
            if response is not None
            else "No response body"
        )

        return {
            "success": False,
            "error": f"SPARQL endpoint returned an HTTP error: {error}",
            "details": details,
        }

    except requests.exceptions.RequestException as error:
        return {
            "success": False,
            "error": (
                "Network error while contacting the SPARQL "
                f"endpoint: {error}"
            ),
        }

    except Exception as error:
        return {
            "success": False,
            "error": f"Unexpected error: {error}",
        }


def lambda_handler(
    event: dict[str, Any],
    context: Any,
) -> dict[str, Any]:
    if not isinstance(event, dict):
        return {
            "success": False,
            "error": "Invalid request: expected a JSON object",
        }

    statement = event.get("sparql")

    if not isinstance(statement, str) or not statement.strip():
        return {
            "success": False,
            "error": (
                "Missing or invalid required parameter: sparql"
            ),
        }

    return execute(statement.strip())