# SolrSynchronizedDisruption
Work done for Solr SynchronizedDisruption.  This will invoke a garbage collection via JMX on a Solr instance, while Solr is being queried.  This is cause queries executing to have a longer elapsed time.

# bin/startSolrForDemo.sh
This will take an argument of the Solr install directory, enable JMX then create a 2 node SolrCloud example cluster, with a collection "gettingstarted" that has 2 shards and a replication factor of 1.

# QueryCauseGC
This will actually connect to the Solr cluster and the JMX port on a Solr instance and cause a garbage collection while the cluster is being queried and indexed.  The data indexed will be committed at the end, when indexing threads and querying threads are killed.

## Arguments
| Argument  | Argument Default | Argument Description |
| ------------- | ------------- | -------------------- |
| collection  | gettingstarted  | Name of the Solr Collection |
| Content Cell  | Content Cell  |JMX Port of a Solr Node |
| zkhosts | localhost:9983 | Comma Separated Zookeeper Hosts |
| chroot | / | Chroot of Solr in Zookeeper |
| queryeventname | Query | Query Event Title |
| gceventname | GC Invoked | Garbage Collection Event Title |
| indexthreadcount | 2 | Number of Threads to Run Indexing |
| querythreadcount | 1 | Number of Threads to Run Queries |

# Resources
1. https://dzone.com/articles/retrieving-jmx-information 
2. http://blog.mague.com/?p=704
3. http://blog.mague.com/?p=696
