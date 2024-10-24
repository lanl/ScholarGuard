#!/usr/bin/env bash
export FLASK_APP=researcher_pod
export FLASK_DEBUG=true
export FLASK_THREADED=true
export RESEARCHER_POD_CONFIG="/Users/harihar/dropbox/Projects/orchestrator/conf/config_standalone.yaml"
export RESEARCHER_POD_SECRETS="/Users/harihar/dropbox/Projects/orchestrator/secrets/secrets"

flask run