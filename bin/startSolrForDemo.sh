#!/usr/bin/env bash

set -e;

#Note: This is to be ran in the Solr install directory

if [ "$#" -ne 1 ]; then
	echo "Takes 1 argument, the solr install directory"
	exit 1;
fi

SOLR_INSTALL_DIR=$1
echo "cd'ing to Solr Install Directory"
cd $SOLR_INSTALL_DIR

echo 'Enabling JMX for Solr, JMX ports are Solr ports + 10000'
echo 'ENABLE_REMOTE_JMX_OPTS="true"' >> ./bin/solr.in.sh

echo -e "2\n8983\n7574\ngettingstarted\n2\n1\n_default" | bin/solr start -c -e cloud

echo "Unsetting updateHandler.autoSoftCommit.maxTime";
curl -X POST -H "Content-Type: application/json" localhost:8983/solr/gettingstarted/config -d "{"unset-property":"updateHandler.autoSoftCommit.maxTime"}"

