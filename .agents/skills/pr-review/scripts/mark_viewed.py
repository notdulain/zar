#!/usr/bin/env python3
"""Mark an explicitly reviewed, unchanged GitHub PR diff as Viewed; dry-run by default."""

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path


FILES_QUERY = """
query($owner:String!, $repo:String!, $number:Int!, $after:String) {
  repository(owner:$owner, name:$repo) {
    pullRequest(number:$number) {
      id headRefOid
      files(first:100, after:$after) {
        nodes { path viewerViewedState }
        pageInfo { hasNextPage endCursor }
      }
    }
  }
}
"""


def graphql(query, variables=None):
    response = subprocess.run(
        ["gh", "api", "graphql", "--input", "-"],
        input=json.dumps({"query": query, "variables": variables or {}}),
        text=True, capture_output=True, check=True,
    )
    result = json.loads(response.stdout)
    if result.get("errors"):
        raise ValueError(f"GitHub GraphQL error: {result['errors']}")
    return result["data"]


def read_files(owner, repo, number, expected_head):
    files, cursors = [], set()
    cursor, pull_request_id = None, None
    while True:
        data = graphql(FILES_QUERY, {
            "owner": owner, "repo": repo, "number": number, "after": cursor,
        })
        repository = data.get("repository")
        pr = repository.get("pullRequest") if repository else None
        if not pr:
            raise ValueError("Pull request was not found or cannot be accessed")
        if pr["headRefOid"] != expected_head:
            raise ValueError("PR head changed; review the new diff before marking files Viewed")
        if pull_request_id is not None and pr["id"] != pull_request_id:
            raise ValueError("Pull request identity changed during pagination")
        pull_request_id = pr["id"]
        connection = pr["files"]
        files.extend(connection["nodes"])
        page = connection["pageInfo"]
        if not page["hasNextPage"]:
            break
        cursor = page["endCursor"]
        if not cursor or cursor in cursors:
            raise ValueError("GitHub returned an invalid/repeated files cursor")
        cursors.add(cursor)
    if len({f["path"] for f in files}) != len(files):
        raise ValueError("GitHub returned duplicate file paths")
    return pull_request_id, files


def mark_reviewed(owner, repo, number, head, reviewed, apply=False):
    if not isinstance(reviewed, list) or not all(isinstance(p, str) and p for p in reviewed):
        raise ValueError("Reviewed manifest must be a JSON array of nonempty path strings")
    if len(set(reviewed)) != len(reviewed):
        raise ValueError("Reviewed manifest contains duplicate paths")
    pr_id, files = read_files(owner, repo, number, head)
    actual = {f["path"] for f in files}
    if set(reviewed) != actual:
        raise ValueError(json.dumps({
            "not_in_reviewed_manifest": sorted(actual - set(reviewed)),
            "not_in_current_diff": sorted(set(reviewed) - actual),
        }))
    if not apply:
        return {"mode": "dry-run", "head": head, "count": len(files), "paths": sorted(actual)}

    pending = [f["path"] for f in files if f["viewerViewedState"] != "VIEWED"]
    for start in range(0, len(pending), 10):
        # Catch concurrent updates before each batch, not only at the end.
        read_files(owner, repo, number, head)
        operations = []
        for i, path in enumerate(pending[start:start + 10]):
            operations.append(
                f"f{i}: markFileAsViewed(input:{{pullRequestId:{json.dumps(pr_id)},"
                f"path:{json.dumps(path)}}}) {{ clientMutationId }}"
            )
        graphql("mutation { " + " ".join(operations) + " }")

    _, final = read_files(owner, repo, number, head)
    if {f["path"] for f in final} != actual:
        raise ValueError("PR file list changed; review it again")
    if any(f["viewerViewedState"] != "VIEWED" for f in final):
        raise ValueError("GitHub did not mark every reviewed file Viewed")
    return {"mode": "applied", "head": head, "viewed": len(final), "total": len(final)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", default="notdulain/zar", help="owner/repository")
    parser.add_argument("--pr", type=int, required=True)
    parser.add_argument("--head", required=True, help="Exact reviewed 40-character head SHA")
    parser.add_argument("--reviewed-file", type=Path, required=True, help="JSON array of reviewed paths")
    parser.add_argument("--apply", action="store_true", help="Update GitHub; otherwise only inspect")
    args = parser.parse_args()
    if not re.fullmatch(r"[^/\s]+/[^/\s]+", args.repo) or args.pr < 1:
        parser.error("Provide owner/repository and a positive PR number")
    if not re.fullmatch(r"[0-9a-f]{40}", args.head):
        parser.error("--head must be the exact lowercase 40-character reviewed SHA")
    try:
        reviewed = json.loads(args.reviewed_file.read_text())
        owner, repo = args.repo.split("/")
        print(json.dumps(mark_reviewed(owner, repo, args.pr, args.head, reviewed, args.apply), indent=2))
    except (ValueError, KeyError, OSError, subprocess.CalledProcessError) as error:
        print(f"Cannot complete Viewed update: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
