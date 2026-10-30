#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p native-build
"${CXX:-c++}" -std=c++17 -Wall -Wextra -Werror -pthread tests/wire_test.cpp -o native-build/wire-test
native-build/wire-test
