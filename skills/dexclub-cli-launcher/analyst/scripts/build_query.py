#!/usr/bin/env python3
from __future__ import annotations

from query_builder import build_query, create_query_parser, dump_query, write_query_output


def main() -> None:
    parser = create_query_parser()
    args = parser.parse_args()
    query = build_query(args.kind, args)
    write_query_output(dump_query(query), getattr(args, "output", None))


if __name__ == "__main__":
    main()
