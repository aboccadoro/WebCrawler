package crawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.URL;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * Implementation of a web crawler based off the Hyperskill.org project
 */
class WebCrawler extends JFrame {

    // Regex to extract href attributes from anchor tags
    private static final Pattern href = Pattern.compile("(?Uis).*href=(['\"])(.*?)\\1.*"); //      : <a href="https://example.com/page.html">example</a>
    // Regex matching relative links without '/'
    private static final Pattern domain = Pattern.compile("(?Ui)^[^/]+"); //                     : example.com
    // Regex matching links missing a protocol
    private static final Pattern no_scheme = Pattern.compile("(?Ui)^//.+"); //                     : //example.com/page.html
    // Regex matching relative links starting with '/'
    private static final Pattern slash_subdirectory = Pattern.compile("(?Ui)^/[^/]+.*"); //        : /page.html
    // Regex matching relative links not starting with a '/'
    private static final Pattern noslash_subdirectory = Pattern.compile("(?Ui)^[^/]+.+/[^/]*"); // : page.html

    // Initialize swing variables accessed in Task
    private static final JTextField url_text = new JTextField();
    private static final JButton upload_button = new JButton("Upload");
    private static final JLabel parsed_pages_updater = new JLabel("0");
    private static final JLabel database_label_updater = new JLabel("0%");
    private static final JButton run_button = new JButton("Run");
    private static final JCheckBox time_limit_toggle = new JCheckBox("Enabled");

    // Initialize all Swing entities updating the UI & I/O
    private static final JTextField workers_text = new JTextField();
    private static final  JTextField depth_text = new JTextField();
    private static final JCheckBox depth_toggle = new JCheckBox("Enabled");
    private static final JTextField time_limit_text = new JTextField();
    private static final JLabel elapsed_time_updater = new JLabel("0:00");
    private static final JLabel pages_added_updater = new JLabel("0");
    private static final JLabel redundant_pages_updater = new JLabel("0");


    // Initialize variables associated with performing the crawl
    private static int max_depth = 0;
    private static final Timer timer = new Timer(1000, null);
    private static ThreadPoolExecutor workers = (ThreadPoolExecutor)Executors.newFixedThreadPool(1);

    // Initialize HashMap for maintaining concurrency
    private final ConcurrentHashMap<String, String> crawled_pages = new ConcurrentHashMap<>();

    // Constructs a WebCrawler with UI components
    private WebCrawler() {
        // Initialize an empty JFrame window and set it to visible
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        setTitle("Web Crawler");
        setVisible(true);

        // Initialize the JLabels, which do not change
        final var url_label = new JLabel("Start URL: ");
        final var workers_label = new JLabel("Workers: ");
        final var depth_label = new JLabel("Maximum depth:  ");
        final var time_limit_label = new JLabel("Time limit: ");
        final var seconds_label = new JLabel("seconds");
        final var elapsed_time_label = new JLabel("Elapsed time: ");
        final var parsed_pages_label = new JLabel("Parsed pages: ");
        final var database_label = new JLabel("Database: ");
        final var pages_added_label = new JLabel("Added:");
        final var redundant_pages_label = new JLabel("Redundant:");

        // Below is the configuration of the layout within the JFrame window. Using a
        // GroupLayout manager, JComponents are added to JPanels when necessary and
        // are aligned horizontally and vertically with JComponents that lie on the
        // same axis. If JComponents are added to a JPanel, they are sub-aligned
        // within that JPanel using a GroupLayout manager and the process above.
        final var url_pane = new JPanel();
        final var url_layout = new GroupLayout(url_pane);
        url_pane.setLayout(url_layout);
        url_layout.setAutoCreateGaps(true);
        url_layout.setHorizontalGroup(
                url_layout.createSequentialGroup()
                        .addComponent(url_text)
                        .addComponent(run_button)
        );
        url_layout.setVerticalGroup(
                url_layout.createSequentialGroup()
                        .addGroup(url_layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                .addComponent(url_text)
                                .addComponent(run_button)
                        )
        );
        final var depth_pane = new JPanel();
        final var depth_layout = new GroupLayout(depth_pane);
        depth_pane.setLayout(depth_layout);
        depth_layout.setAutoCreateGaps(true);
        depth_layout.setHorizontalGroup(
                depth_layout.createSequentialGroup()
                        .addComponent(depth_text)
                        .addComponent(depth_toggle)
        );
        depth_layout.setVerticalGroup(
                depth_layout.createSequentialGroup()
                        .addGroup(depth_layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                .addComponent(depth_text)
                                .addComponent(depth_toggle)
                        )
        );
        final var time_limit_pane = new JPanel();
        final var time_limit_layout = new GroupLayout(time_limit_pane);
        time_limit_pane.setLayout(time_limit_layout);
        time_limit_layout.setAutoCreateGaps(true);
        time_limit_layout.setHorizontalGroup(
                time_limit_layout.createSequentialGroup()
                        .addComponent(time_limit_text)
                        .addComponent(seconds_label)
                        .addComponent(time_limit_toggle)
        );
        time_limit_layout.setVerticalGroup(
                time_limit_layout.createSequentialGroup()
                        .addGroup(time_limit_layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                .addComponent(time_limit_text)
                                .addComponent(seconds_label)
                                .addComponent(time_limit_toggle)
                        )
        );
        final var upload_pane = new JPanel();
        final var upload_layout = new GroupLayout(upload_pane);
        upload_pane.setLayout(upload_layout);
        upload_layout.setAutoCreateGaps(true);
        upload_layout.setHorizontalGroup(
                upload_layout.createSequentialGroup()
                        .addComponent(database_label_updater)
                        .addComponent(upload_button)
                        .addComponent(pages_added_label)
                        .addComponent(pages_added_updater)
                        .addComponent(redundant_pages_label)
                        .addComponent(redundant_pages_updater)
        );
        upload_layout.setVerticalGroup(
                upload_layout.createSequentialGroup()
                        .addGroup(upload_layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                .addComponent(database_label_updater)
                                .addComponent(upload_button)
                                .addComponent(pages_added_label)
                                .addComponent(pages_added_updater)
                                .addComponent(redundant_pages_label)
                                .addComponent(redundant_pages_updater)
                        )
        );
        final var input_pane = new JPanel();
        final var input_layout = new GroupLayout(input_pane);
        input_pane.setLayout(input_layout);
        input_layout.setAutoCreateGaps(true);
        input_layout.setAutoCreateContainerGaps(true);
        input_layout.setHorizontalGroup(
                input_layout.createSequentialGroup()
                        .addGroup(input_layout.createParallelGroup()
                                .addComponent(url_label)
                                .addComponent(workers_label)
                                .addComponent(depth_label)
                                .addComponent(time_limit_label)
                                .addComponent(elapsed_time_label)
                                .addComponent(parsed_pages_label)
                                .addComponent(database_label)
                        )
                        .addGroup(input_layout.createParallelGroup()
                                .addComponent(url_pane)
                                .addComponent(workers_text)
                                .addComponent(depth_pane)
                                .addComponent(time_limit_pane)
                                .addComponent(elapsed_time_updater)
                                .addComponent(parsed_pages_updater)
                                .addComponent(upload_pane)
                        )
        );
        input_layout.setVerticalGroup(
                input_layout.createSequentialGroup()
                        .addGroup(input_layout.createParallelGroup(GroupLayout.Alignment.CENTER)
                                .addComponent(url_label)
                                .addComponent(url_pane)
                        )
                        .addGroup(input_layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                .addComponent(workers_label)
                                .addComponent(workers_text)
                        )
                        .addGroup(input_layout.createParallelGroup(GroupLayout.Alignment.CENTER)
                                .addComponent(depth_label)
                                .addComponent(depth_pane)
                        )
                        .addGroup(input_layout.createParallelGroup(GroupLayout.Alignment.CENTER)
                                .addComponent(time_limit_label)
                                .addComponent(time_limit_pane)
                        )
                        .addGroup(input_layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                .addComponent(elapsed_time_label)
                                .addComponent(elapsed_time_updater)
                        )
                        .addGroup(input_layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                .addComponent(parsed_pages_label)
                                .addComponent(parsed_pages_updater)
                        )
                        .addGroup(input_layout.createParallelGroup(GroupLayout.Alignment.CENTER)
                                .addComponent(database_label)
                                .addComponent(upload_pane)
                        )
        );
        // Set the content pane for this JFrame, packing the contents within the window size
        setContentPane(input_pane);
        setSize(500, 250);

        run();
        upload();
    }

    /**
     * Runs the WebCrawler instance on a new thread to not block the Event Dispatch thread.
     *
     * @param args placeholder empty argument array
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(WebCrawler::new);
    }


    /**
     * Defines the action performed when the run_button is clicked. Start/stop functionality
     * is implemented here. The run_button text will change from "Run" to "Stop" and vice
     * versa upon clicking the button. If the button is clicked in a state where work is
     * still being computed, the text will change to "Wait!" until the work has completed,
     * in which case the text will read "Run."
     */
    private void run() {
        final var formatter = new SimpleDateFormat("m:ss");
        final var time = new AtomicLong(1000L);
        final var time_limit = new AtomicLong();
        run_button.addActionListener(e -> {
            try {
                if (run_button.getText().equals("Stop")) {
                    workers.shutdown();
                    workers = (ThreadPoolExecutor)Executors.newFixedThreadPool(1);
                    if (time_limit_toggle.isSelected()) timer.stop();
                    run_button.setText("Run");
                    if (upload_button.getText().equals("Wait!")) upload_button.setText("Upload");
                }
                else if (workers.getActiveCount() > 0) run_button.setText("Wait!");
                else {
                    run_button.setText("Stop");
                    final var worker_count = Integer.parseInt(workers_text.getText());
                    if (worker_count < 1 || worker_count > 100) throw new IllegalArgumentException("worker_count invalid");
                    else if (worker_count > workers.getMaximumPoolSize()) {
                        workers.setMaximumPoolSize(worker_count);
                        workers.setCorePoolSize(worker_count);
                    }
                    else {
                        workers.setCorePoolSize(worker_count);
                        workers.setMaximumPoolSize(worker_count);
                    }
                    workers.prestartAllCoreThreads();
                    elapsed_time_updater.setText("0:00");
                    parsed_pages_updater.setText("0");
                    database_label_updater.setText("0%");
                    pages_added_updater.setText("0");
                    redundant_pages_updater.setText("0");
                    crawled_pages.clear();
                    if (time_limit_toggle.isSelected()) {
                        time_limit.set(Long.parseLong(time_limit_text.getText()));
                        time.set(1000L);
                        if (timer.getActionListeners().length == 0) {
                            timer.addActionListener(evt -> {
                                if (time.get() >= time_limit.get() * 1000L) {
                                    workers.shutdown();
                                    workers = (ThreadPoolExecutor)Executors.newFixedThreadPool(1);
                                    ((Timer) evt.getSource()).stop();
                                    if (run_button.getText().equals("Stop")) run_button.setText("Run");
                                }
                                elapsed_time_updater.setText(formatter.format(time.get()));
                                time.set(time.get() + 1000L);
                            });
                        }
                        timer.restart();
                    }
                    // Quickly test to see if the input is a website
                    new URL(url_text.getText()).openConnection().connect();
                    if (depth_toggle.isSelected()) {
                        max_depth = Integer.parseInt(depth_text.getText());
                        workers.submit(new Task(url_text.getText(), 0));
                    }
                    else workers.submit(new Task(url_text.getText()));
                }
            }
            catch (IllegalArgumentException | RejectedExecutionException | NullPointerException | IOException error) {
                if (workers.getActiveCount() > 0) workers.shutdown();
                workers = (ThreadPoolExecutor)Executors.newFixedThreadPool(1);
                if (time_limit_toggle.isSelected() && timer.getActionListeners().length != 0) timer.stop();
                run_button.setText("Run");
                if (upload_button.getText().equals("Wait!")) upload_button.setText("Upload");
            }
        });
    }

    /**
     * Defines the action performed when the upload_button is clicked.
     */
    private void upload() {
        final var progress = new AtomicInteger();
        final var crawl_size = new AtomicInteger();
        final var pages_added = new AtomicInteger();
        final var redundant_pages = new AtomicInteger();
        upload_button.addActionListener(e -> {
            try {
                if (workers.getActiveCount() == 0) {
                    upload_button.setText("Upload");
                    pages_added_updater.setText("0");
                    pages_added.set(0);
                    redundant_pages_updater.setText("0");
                    redundant_pages.set(0);
                    workers.submit(() -> {
                        try {
                            final var conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/crawled_pages?useSSL=false", "Anthony", "rvbtpbafA13");
                            crawl_size.set(crawled_pages.size());
                            progress.set(0);
                            for (var entry : crawled_pages.entrySet()) {
                                try {
                                    final var ps = conn.prepareStatement("insert into pages (Title, URL) values (?, ?)");
                                    ps.setObject(1, entry.getValue());
                                    ps.setObject(2, entry.getKey());
                                    ps.executeUpdate();
                                    pages_added_updater.setText(String.valueOf(pages_added.incrementAndGet()));
                                }
                                catch (SQLException dup) {
                                    // if the error code is a duplicate entry error this page is a duplicate
                                    if (dup.getErrorCode() == 1062) redundant_pages_updater.setText(String.valueOf(redundant_pages.incrementAndGet()));
                                }
                                database_label_updater.setText((int)(((double)progress.incrementAndGet() / crawl_size.get()) * 100) + "%");
                            }
                            // close the jdbc connection and free up used resources immediately
                            conn.close();
                            if (upload_button.getText().equals("Wait!")) upload_button.setText("Upload");
                            if (run_button.getText().equals("Wait!")) run_button.setText("Run");
                        }
                        catch (SQLException ignored) {}
                    });
                }
                else if (!upload_button.getText().equals("Wait!")) upload_button.setText("Wait!");
            }
            catch (NullPointerException ignored) {}
        });
    }

    /**
     * When links are acquired during the crawl, it is necessary to validate them
     * before submitting them as Tasks. Validation ensures the steps to attempt a
     * valid connection are made before attempting (and potentially failing) a
     * connection to the webpage associated with that link.
     *
     * @param link link to validate
     * @param url parent of link
     * @return link either validated or not
     */
    private String validateLink(String link, String url) {
        final var scheme = url.contains("://") ? url.substring(0, url.indexOf("://")) : "https";
        final var domain_matcher = domain.matcher(link);
        final var no_scheme_matcher = no_scheme.matcher(link);
        final var slash_subdirectory_matcher = slash_subdirectory.matcher(link);
        final var noslash_subdirectory__matcher = noslash_subdirectory.matcher(link);
        if (domain_matcher.matches() || (noslash_subdirectory__matcher.matches() && !link.contains("://"))) {
            var index = url.lastIndexOf('/');
            if (index > 0 && url.charAt(index - 1) != '/') return url.substring(0, index + 1) + link;
            else return url + '/' + link;
        }
        else if (no_scheme_matcher.matches()) return scheme + ':' + link;
        else if (slash_subdirectory_matcher.matches()) {
            final var index = url.lastIndexOf('/');
            if (index > 0 && url.charAt(index - 1) != '/') return url.substring(0, index) + link;
            else return url + link;
        }
        return link;
    }

    /**
     * Helper method for checking redundancy when submitting work to the worker threads.
     *
     * @param url the url to connect to
     * @return whether the url is redundant work or not
     */
    private boolean isRedundant(String url, Document doc) {
        // If the Task is not set to die, and the url has not been crawled
        // yet, add it to the list of crawled_pages as it will be crawled,
        // otherwise mark the url as redundant as it has already been seen
        if (!crawled_pages.containsKey(url)) {
            crawled_pages.put(url, doc.title());
            parsed_pages_updater.setText(String.valueOf(crawled_pages.size()));
            return false;
        }
        return true;
    }

    /**
     * Main algorithm for crawling a webpage. Links are extracted from the doc by using
     * Jsoup's cssQuery selector on anchor tags with href attributes. The value of each href
     * is then extracted and either submitted to another worker thread, or, if the depth
     * of the child link is equal to the set max depth, the link is crawled within the
     * current worker Task, effectively stopping worker Task submissions.
     *
     * @param doc document connected to url
     * @param url url used to connect to doc
     * @param depth depth of the current url
     * @throws RejectedExecutionException if the work could not be executed
     */
    private void crawl(String url, Document doc, Integer depth) {
        // Obtain all the href attributes in anchor tags found in the url's html
        final var links = doc.select("a[href]");
        for (var link : links) {
            // Obtain the actual href value from this link's anchor tag
            final var href_matcher = href.matcher(link.toString());
            if (href_matcher.matches()) {
                try {

                    // Validate the matched href value and store it
                    final var valid = validateLink(href_matcher.group(2), url);
                    // Create the appropriate worker task for this valid link based on window input
                    if (depth == null) workers.submit(new Task(valid));
                    else if (depth < max_depth) workers.submit(new Task(valid, depth + 1));
                    // If the current depth is equal to max_depth we stop submitting work
                }
                catch (IndexOutOfBoundsException | RejectedExecutionException ignored) {}
            }
        }
    }

    /**
     * Represents a Task thread to be submitted as a worker of a threadpool.
     */
    class Task implements Runnable {

        private final String url; // Url to connect to and acquire links from
        private final Integer depth; // The current depth of this url

        // Constructs a Task with the current depth
        private Task(String url, Integer depth) {
            this.url = url;
            this.depth = depth;
        }

        // Constructs a Task ignoring depth
        private Task(String url) {
            this(url, null);
        }

        @Override
        public void run() {
            try {
                // Create a Document representing a connection to the url
                final var doc = Jsoup.connect(url).userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:63.0) Gecko/20100101 Firefox/63.0").get();
                // If already seen skip the url
                if (!isRedundant(url, doc)) crawl(url, doc, depth);
            }
            // Ignore URLs that fail to connect
            catch (IOException ignored) {}
            // If this Task is the only task running at this point and there is no
            // more work to be done, the UI updates to reflect the completion
            if (workers.getActiveCount() == 1 && workers.getQueue().size() == 0) {
                if (time_limit_toggle.isSelected()) timer.stop();
                if (!run_button.getText().equals("Run")) run_button.setText("Run");
                if (!upload_button.getText().equals("Upload")) upload_button.setText("Upload");
            }
        }
    }
}