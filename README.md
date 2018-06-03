# SolrSynchronizedDisruption
Work done for Solr SynchronizedDisruption.  This will invoke a garbage collection via JMX on a Solr instance, while Solr is being queried.

# bin/startSolrForDemo.sh
This will take an argument of the Solr install directory, enable JMX then create a 2 node SolrCloud example cluster, with a collection "gettingstarted" that has 2 shards and a replication factor of 1.

# QueryCauseGC

## Arguments


# Resources
1. http://blog.mague.com/?p=696
2. http://blog.mague.com/?p=704
3.
