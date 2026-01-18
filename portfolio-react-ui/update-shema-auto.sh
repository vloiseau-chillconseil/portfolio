#!/bin/bash
set -e

npx get-graphql-schema http://127.0.0.1:7524/graphql > schema-auto.graphql