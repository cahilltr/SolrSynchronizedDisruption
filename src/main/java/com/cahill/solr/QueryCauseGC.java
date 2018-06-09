package com.cahill.solr;

import org.apache.commons.cli.*;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrInputDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

public class QueryCauseGC {
    private static final Logger logger = LoggerFactory.getLogger(QueryCauseGC.class);

    private static final String[] wordLib = new String[]{"The", "Word", "Is", "stop", "Bigword", "Solr", "Lucene", "wordly", "burn", "alter"};
    private static final int wordLibArrayLength = wordLib.length - 1;

    private static final List<Event> eventList = Collections.synchronizedList(new ArrayList<>());

    private static final String COLLECTIONS_OPT = "collection";
    private static final String COLLECTION_DEFAULT = "gettingstarted";

    private static final String JMX_PORT_OPT = "jmxport";
    private static final String JMX_PORT_DEFAULT = "17574";

    private static final String ZK_HOSTS_OPT = "zkhosts";
    private static final String ZK_HOSTS_DEFAULT = "localhost:9983";

    private static final String CH_ROOT_OPT = "chroot";
    private static final String CH_ROOT_DEFAULT = "/";

    private static final String QUERY_EVENT_DEFAULT = "Query";
    private static final String QUERY_EVENT_OPT = "queryeventname";

    private static final String GC_EVENT_DEFAULT = "GC Invoked";
    private static final String GC_EVENT_OPT = "gceventname";

    private static final String INDEX_THREAD_COUNT_OPT = "indexthreadcount";
    private static final String INDEX_THREAD_COUNT_DEFAULT = "2";

    private static final String QUERY_THREAD_COUNT_OPT = "querythreadcount";
    private static final String QUERY_THREAD_COUNT_DEFAULT = "1";

    public static void main(String[] args) {

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd;
        try {
            cmd = parser.parse(getOptions(), args);
        } catch (ParseException e) {
            logger.error("ParseException", e);
            return;
        }

        QueryCauseGC queryCauseGC = new QueryCauseGC();
        queryCauseGC.run(cmd);
        System.exit(0);
    }

    private void run(CommandLine cmd) {
        String collection = cmd.getOptionValue(COLLECTIONS_OPT, COLLECTION_DEFAULT);
        String jmxPort = cmd.getOptionValue(JMX_PORT_OPT, JMX_PORT_DEFAULT);
        String chRoot = cmd.getOptionValue(CH_ROOT_OPT, CH_ROOT_DEFAULT);
        String queryEventName = cmd.getOptionValue(QUERY_EVENT_OPT, QUERY_EVENT_DEFAULT);
        String gcEventName = cmd.getOptionValue(GC_EVENT_OPT, GC_EVENT_DEFAULT);
        int indexThreadCount = Integer.parseInt(cmd.getOptionValue(INDEX_THREAD_COUNT_OPT, INDEX_THREAD_COUNT_DEFAULT));
        int queryThreadCount = Integer.parseInt(cmd.getOptionValue(QUERY_THREAD_COUNT_OPT, QUERY_THREAD_COUNT_DEFAULT));
        String zkHosts = cmd.getOptionValue(ZK_HOSTS_OPT, ZK_HOSTS_DEFAULT);
        String[] zkHostsArray = zkHosts.split(",");

        List<String> zkStringList = Arrays.asList(zkHostsArray);
        try (CloudSolrClient cloudSolrClient =
                     new CloudSolrClient.Builder(zkStringList, Optional.of(chRoot)).build()) {
            cloudSolrClient.connect();

            Thread tIndex = new Thread(() -> {
//                while (!interrupted()) {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        addData(cloudSolrClient, collection);
                        Thread.sleep(10);
                    }
                } catch (InterruptedException e) {
                    logger.info("InterruptedException", e);
                    return;
                }
            });
            List<Thread> indexThreads = new ArrayList<>(indexThreadCount);
            IntStream.of(indexThreadCount).forEach(i -> {
                Thread tIndexRunning = new Thread(tIndex);
                tIndexRunning.start();
                indexThreads.add(tIndexRunning);
            });

            Thread tQuery = new Thread(() -> {
//                while (!interrupted()) {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        query(cloudSolrClient, collection, queryEventName);
                        Thread.sleep(10);
                    }
                } catch (Exception e) {
                    logger.info("Exception", e);
                    return;
                }
            });

            List<Thread> queryThreads = new ArrayList<>(queryThreadCount);
            IntStream.of(queryThreadCount).forEach(i -> {
                Thread queryThread = new Thread(tQuery);
                tQuery.start();
                queryThreads.add(queryThread);
            });

            // Sleep to let Solr warm up with the queries
            Thread.sleep(10000);

            Thread tJMXGC = new Thread(() -> {
                try {
                    JMXServiceURL url = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://localhost:" + jmxPort + "/jmxrmi");
                    try (JMXConnector jmxc = JMXConnectorFactory.connect(url, null)) {
                        jmxc.connect();

                        MBeanServerConnection server = jmxc.getMBeanServerConnection();
                        long start = System.currentTimeMillis();
                        server.invoke(ObjectName.getInstance("java.lang:type=Memory"), "gc", null, null);
                        eventList.add(new Event(start, System.currentTimeMillis(), gcEventName));
                    }
                } catch (Exception e) {
                    logger.info("Exception", e);
                }
            });

            tJMXGC.start();
            tJMXGC.join();

            Thread.sleep(1000);
            indexThreads.forEach(Thread::interrupt);
            queryThreads.forEach(Thread::interrupt);

            for (Thread indexThread : indexThreads) {
                indexThread.join();
            }
            for (Thread queryThread : queryThreads) {
                queryThread.join();
            }

            Collections.sort(eventList);

            StringBuilder stringBuilder = new StringBuilder(System.lineSeparator());
            for (Event e : eventList) {
                stringBuilder.append(e.toString()).append(System.lineSeparator());
            }
            System.out.println(stringBuilder.toString());
            cloudSolrClient.commit(collection);
            cloudSolrClient.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void query(CloudSolrClient cloudSolrClient, String collection, String preQueryLog)
            throws IOException, SolrServerException {
        SolrQuery query = new SolrQuery();
        query.setQuery("*:*");
        query.addFacetField("myNumeric_i");
        query.addFacetField("mytext_t");
        query.addGetFieldStatistics("myNumeric_i");
        query.add("json.facet", "{" +
                "  x : \"avg(myNumeric_i)\"," +
                "  y : \"unique(myNumeric_i)\"" +
                "}");
        long start = System.currentTimeMillis();
        QueryResponse response;
        response = cloudSolrClient.query(collection, query);
        String event = preQueryLog + "  QTime: " + response.getQTime() + ", Elapsed Time: " + response.getElapsedTime();
        eventList.add(new Event(start, System.currentTimeMillis(), event));
    }

    private void addData(CloudSolrClient cloudSolrClient, String collection) {
        try {
            SolrInputDocument sid = new SolrInputDocument();
            sid.setField("id", UUID.randomUUID().toString());
            int randomNum = ThreadLocalRandom.current().nextInt(0, wordLibArrayLength);
            StringBuilder randText = new StringBuilder();
            for (int i = 0; i < randomNum; i++) {
                randText.append(" ").append(wordLib[ThreadLocalRandom.current().nextInt(0, wordLibArrayLength)]);
            }
            sid.setField("mytext_t", randText.toString().trim());
            sid.setField("myNumeric_i", randomNum);
            cloudSolrClient.add(collection, sid);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Options getOptions() {
        Options options = new Options();

        options.addOption(COLLECTIONS_OPT, COLLECTIONS_OPT,true, "Name of the Solr Collection");
        options.addOption(JMX_PORT_OPT, JMX_PORT_OPT,true, "JMX Port of a Solr Node");
        options.addOption(ZK_HOSTS_OPT, ZK_HOSTS_OPT,true, "Comma Separated Zookeeper Hosts");
        options.addOption(CH_ROOT_OPT, CH_ROOT_OPT,true, "Chroot of Solr in Zookeeper");
        options.addOption(GC_EVENT_OPT, GC_EVENT_OPT,true, "");
        options.addOption(QUERY_EVENT_OPT, QUERY_EVENT_OPT,true, "");
        options.addOption(QUERY_THREAD_COUNT_OPT, QUERY_THREAD_COUNT_OPT,true, "Number of Threads to Run Querying on");
        options.addOption(INDEX_THREAD_COUNT_OPT, INDEX_THREAD_COUNT_OPT,true, "Number of Threads to Run Indexing on");

        return options;
    }

    static class Event implements Comparable<Event> {
        long eventStartTime;
        long eventEndTime;
        String event;

        Event(long eventStartTime, long eventEndTime, String event){
            this.event = event;
            this.eventStartTime = eventStartTime;
            this.eventEndTime = eventEndTime;
        }

        @Override
        public int compareTo(Event o) {
            return Long.compare(this.eventStartTime, o.eventStartTime);
        }

        @Override
        public String toString() {
            return "Start: " + this.eventStartTime + " End: " + this.eventEndTime + " " + this.event;
        }
    }
}
