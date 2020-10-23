#!/usr/bin/env bash

cp ../../../target/graalvm-native-image/trace4cats-agent-kafka .

docker build -t janstenpickle/trace4cats-agent-kafka:$GITHUB_RUN_NUMBER .
docker push janstenpickle/trace4cats-agent-kafka:$GITHUB_RUN_NUMBER