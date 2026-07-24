import json
import os
from typing import Any

import requests
from dotenv import load_dotenv


load_dotenv()

REQUEST_TIMEOUT_SECONDS = 240
MAX_ERROR_LENGTH = 1000


def escape_sparql_string(value: str) -> str:
    """
    Escapes a Python string for use inside a double-quoted SPARQL literal.

    This prevents quotes, backslashes, and control characters in a location
    name from breaking or modifying the generated SPARQL query.
    """
    return (
        value
        .replace("\\", "\\\\")
        .replace('"', '\\"')
        .replace("\r", "\\r")
        .replace("\n", "\\n")
        .replace("\t", "\\t")
    )


def execute(name: str) -> dict[str, Any]:
    jena_url = os.getenv("JENA_URL")

    if not jena_url:
        return {
            "success": False,
            "error": "JENA_URL is not set",
        }

    escaped_name = escape_sparql_string(name)

    statement = f"""
    PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
    PREFIX ex: <https://localhost:4200/lpg/graph_801104/0/rdfs#>
    PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
    PREFIX text: <http://jena.apache.org/text#>
    PREFIX lpgs: <https://localhost:4200/lpg/rdfs#>
    PREFIX lpgv: <https://localhost:4200/lpg/graph_801104/0#>
    PREFIX apex: <http://dime.usace.mil/data/dataset#>

    SELECT ?code ?type ?s
    FROM lpgv:
    FROM apex:APEX_prism
    WHERE {{
      BIND("{escaped_name}" AS ?search)

      {{
        (?s ?score) text:query (rdfs:label ?search) .
      }}
      UNION
      {{
        ?s lpgs:GeoObject-code ?search .
        BIND(1000000 AS ?score)
      }}
      UNION
      {{
        ?s skos:altLabel ?search .
        BIND(1000000 AS ?score)
      }}

      OPTIONAL {{ ?s lpgs:GeoObject-code ?geoCode . }}
      OPTIONAL {{ ?s skos:altLabel ?altCode . }}
      BIND(COALESCE(?geoCode, ?altCode) AS ?code)

      ?s a ?type .
    }}
    ORDER BY DESC(?score)
    LIMIT 100
    """

    response: requests.Response | None = None

    try:
        response = requests.post(
            jena_url,
            data={"query": statement},
            headers={
                "Accept": "application/sparql-results+json",
            },
            timeout=REQUEST_TIMEOUT_SECONDS,
        )

        response.raise_for_status()

        try:
            response_json = response.json()
        except ValueError:
            return {
                "success": False,
                "error": "Jena returned a non-JSON response",
                "details": response.text[:MAX_ERROR_LENGTH],
            }

        results = []

        for binding in response_json.get("results", {}).get("bindings", []):
            code = binding.get("code", {}).get("value")
            object_type = binding.get("type", {}).get("value")
            uri = binding.get("s", {}).get("value")

            # The query makes type and s required, but code is derived from
            # optional properties and therefore may be missing.
            if not object_type or not uri:
                continue

            results.append({
                "code": code,
                "type": object_type,
                "uri": uri,
            })

        return {
            "success": True,
            "query": name,
            "results": results,
            "resultCount": len(results),
        }

    except requests.exceptions.HTTPError as error:
        details = (
            response.text[:MAX_ERROR_LENGTH]
            if response is not None
            else "No response body"
        )

        return {
            "success": False,
            "error": f"Jena returned an HTTP error: {error}",
            "details": details,
        }

    except requests.exceptions.RequestException as error:
        return {
            "success": False,
            "error": f"Network error while contacting Jena: {error}",
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
    print("EVENT:", json.dumps(event, default=str))

    if not isinstance(event, dict):
        return {
            "success": False,
            "error": "Invalid request: expected a JSON object",
        }

    name = event.get("name")

    if not isinstance(name, str) or not name.strip():
        return {
            "success": False,
            "error": "Missing or invalid required parameter: name",
        }

    return execute(name.strip())