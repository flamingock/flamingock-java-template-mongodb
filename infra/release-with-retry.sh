#!/bin/bash

maxAttempts=${1:-3}
waitingSeconds=${2:-20}

echo "Releasing bundle to Central Portal with max attempts[$maxAttempts] and $waitingSeconds seconds delay"
for (( i=1; i<=maxAttempts; i++ )); do
  if ./gradlew jreleaserDeploy --no-daemon --stacktrace; then
    exit 0
  fi
  if [ "$i" -eq "$maxAttempts" ]; then
    echo "Failed release after $maxAttempts maxAttempts"
    exit 1
  fi
  echo "Retrying in $waitingSeconds seconds..."
  sleep "$waitingSeconds"
  echo
  echo "********************************************************************************** RELEASE ATTEMPT($((i + 1))) **********************************************************************************"
done
