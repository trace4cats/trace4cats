#!/usr/bin/env bash

cp ../../../target/graalvm-native-image/trace4cats-agent .

docker build -t janstenpickle/trace4cats-agent:$GITHUB_RUN_NUMBER .
docker push janstenpickle/trace4cats-agent:$GITHUB_RUN_NUMBER