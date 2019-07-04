package crawler;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/**
 * Implementation of a web crawler based off the Hyperskill.org project
 */
class WebCrawler extends JFrame {

    // Regex to extract href attributes from anchor tags
    private final Pattern href_pattern = Pattern.compile("(?Uis).*href=(['\"])(.*?)\\1.*"); //  : <a href="https://example.com/page.html">example</a>
    // Regex matching relative links without '/'
    private final Pattern relative_pattern = Pattern.compile("(?Ui)^([^/]+)"); //               : example.com, page.html
    // Regex matching links missing a protocol
    private final Pattern no_protocol_pattern = Pattern.compile("(?Ui)^//.+"); //               : //example.com/page.html
    // Regex matching relative links starting with '/'
    private final Pattern nosource_slash_pattern = Pattern.compile("(?Ui)^/[^/]+.*"); //        : /page.html
    // Regex matching relative links not starting with a '/'
    private final Pattern nosource_noslash_pattern = Pattern.compile("(?Ui)^[^/]+.+/[^/]*"); // : page.html

    // Initialize swing variables accessed in Task
    private final JTextField url_text = new JTextField();
    private final JLabel parsed_pages_updater = new JLabel("0");
    private final JToggleButton run_button = new JToggleButton("Run");
    private final JCheckBox time_limit_toggle = new JCheckBox("Enabled");

    // Initialize variables associated with performing the crawl
    private int max_depth = 0;
    private final AtomicReference<Timer> timer = new AtomicReference<>();
    private final AtomicInteger worker_count = new AtomicInteger(0);
    private final AtomicReference<ThreadPoolExecutor> workers = new AtomicReference<>();

    // Initialize volatile variables for maintaining concurrency
    private volatile boolean kill_all = false;
    private volatile Hashtable<String, String> crawled_pages = new Hashtable<>();

    // Constructs a WebCrawler with UI components
    WebCrawler() {
        // Initialize an empty JFrame window and set it to visible
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        setTitle("Web Crawler");
        setVisible(true);

        // Initialize Swing variables not used inside the Task class
        final var url_label = new JLabel("Start URL: ");
        final var workers_label = new JLabel("Workers: ");
        final var workers_text = new JTextField();
        final var depth_label = new JLabel("Maximum depth:  ");
        final var depth_text = new JTextField();
        final var depth_toggle = new JCheckBox("Enabled");
        final var time_limit_label = new JLabel("Time limit: ");
        final var time_limit_text = new JTextField();
        final var seconds_label = new JLabel("seconds");
        final var elapsed_time_label = new JLabel("Elapsed time: ");
        final var elapsed_time_updater = new JLabel("0:00");
        final var parsed_pages_label = new JLabel("Parsed pages: ");
        final var export_label = new JLabel("Export: ");
        final var export_text = new JTextField();
        final var save_button = new JButton("Save");

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
        final var export_pane = new JPanel();
        final var export_layout = new GroupLayout(export_pane);
        export_pane.setLayout(export_layout);
        export_layout.setAutoCreateGaps(true);
        export_layout.setHorizontalGroup(
                export_layout.createSequentialGroup()
                        .addComponent(export_text)
                        .addComponent(save_button)
        );
        export_layout.setVerticalGroup(
                export_layout.createSequentialGroup()
                        .addGroup(export_layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                .addComponent(export_text)
                                .addComponent(save_button)
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
                                .addComponent(export_label)
                        )
                        .addGroup(input_layout.createParallelGroup()
                                .addComponent(url_pane)
                                .addComponent(workers_text)
                                .addComponent(depth_pane)
                                .addComponent(time_limit_pane)
                                .addComponent(elapsed_time_updater)
                                .addComponent(parsed_pages_updater)
                                .addComponent(export_pane)
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
                                .addComponent(export_label)
                                .addComponent(export_pane)
                        )
        );
        // Set the content pane for this JFrame, packing the contents within the window size
        setContentPane(input_pane);
        setSize(500, 250);

        run(workers_text, depth_text, depth_toggle, time_limit_text, elapsed_time_updater);
        save(export_text, save_button);
    }

    /**
     * Defines the action performed when the run_button is clicked. An item listener is
     * added to this JToggleButton to allow for run/stop functionality. The system starts
     * with the run_button unselected and with text set to "Run". After being clicked, the
     * text changes to "Stop" and again is set to unselected. The reason to set the button's
     * selected state to false is to ensure the differentiation when starting and stopping.
     * By deselecting the button upon every action the button has a more aesthetic appeal
     * in the UI and works in tandem with the button's text to control starting/stopping.
     *
     * @param workers_text amount of worker threads to use
     * @param depth_text maximum depth
     * @param depth_toggle toggles the use of depth_text
     * @param time_limit_text time limit
     * @param elapsed_time_updater updates the UI based on timer
     */
    private void run(JTextField workers_text, JTextField depth_text, JCheckBox depth_toggle, JTextField time_limit_text, JLabel elapsed_time_updater) {
        final var formatter = new SimpleDateFormat("m:ss");
        final var time = new AtomicLong(1000L);
        final var start = new AtomicReference<Task>();
        final var time_limit = new AtomicLong();
        run_button.addItemListener(e -> {
            if (run_button.getText().equals("Run") && run_button.isSelected()) {
                run_button.setText("Stop");
                run_button.setSelected(false);
                elapsed_time_updater.setText("0:00");
                parsed_pages_updater.setText("0");
                try {
                    worker_count.set(Integer.parseInt(workers_text.getText()));
                    if (worker_count.get() < 1) throw new IllegalArgumentException("worker_count < 1");
                    if (workers.get() == null) workers.set((ThreadPoolExecutor)Executors.newFixedThreadPool(worker_count.get()));
                    else if (workers.get().getCorePoolSize() != worker_count.get()) {
                        if (worker_count.get() > workers.get().getMaximumPoolSize()) workers.get().setMaximumPoolSize(worker_count.get());
                        workers.get().setCorePoolSize(worker_count.get());
                    }
                    kill_all = false;
                    crawled_pages.clear();
                    workers.get().prestartAllCoreThreads();
                    if (time_limit_toggle.isSelected()) {
                        time_limit.set(Long.parseLong(time_limit_text.getText()));
                        time.set(1000L);
                        if (timer.get() == null) {
                            timer.set(new Timer(1000, evt -> {
                                if (time.get() >= time_limit.get() * 1000L) {
                                    kill_all = true;
                                    ((Timer)evt.getSource()).stop();
                                }
                                elapsed_time_updater.setText(formatter.format(time.get()));
                                time.set(time.get() + 1000L);
                            }));
                        }
                        timer.get().restart();
                    }
                    if (depth_toggle.isSelected()) {
                        max_depth = Integer.parseInt(depth_text.getText());
                        start.set(new Task(url_text.getText(), 0));
                        workers.get().submit(start.get());
                    }
                    else {
                        start.set(new Task(url_text.getText()));
                        workers.get().submit(start.get());
                    }
                }
                catch (IllegalArgumentException | RejectedExecutionException error) {
                    if (workers.get() != null) workers.get().getQueue().clear();
                    kill_all = true;
                    if (time_limit_toggle.isSelected() && timer.get() != null) timer.get().stop();
                    run_button.setText("Run");
                }
            }
            else if (run_button.getText().equals("Stop") && run_button.isSelected()) {
                workers.get().getQueue().clear();
                kill_all = true;
                if (time_limit_toggle.isSelected()) timer.get().stop();
            }
        });
    }

    /**
     * Defines the action performed when the export_button is clicked. The button's
     * state is selected to false upon every action for aesthetic appeal in the UI.
     *
     * @param export_text name of the file to create and export to
     * @param export_button export button
     */
    private void save(JTextField export_text, JButton export_button) {
        export_button.addActionListener(e -> {
            try {
                final var output = new OutputStreamWriter(new FileOutputStream(export_text.getText()), StandardCharsets.UTF_8);
                for (var entry : crawled_pages.entrySet()) {
                    output.write(entry.getKey() + "\n");
                    output.write(entry.getValue() + "\n");
                }
                output.close();
            }
            catch (IOException ignored) {}
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
     * @param protocol protocol of url
     * @return link either validated or not
     */
    private String validateLink(String link, String url, String protocol) {
        final var relative_matcher = relative_pattern.matcher(link);
        final var no_protocol_matcher = no_protocol_pattern.matcher(link);
        final var nosource_slash_matcher = nosource_slash_pattern.matcher(link);
        final var nosource_noslash_matcher = nosource_noslash_pattern.matcher(link);
        final var valid = new AtomicReference<>(link);
        if (relative_matcher.matches() || (nosource_noslash_matcher.matches() && !link.contains("://"))) {
            var index = url.lastIndexOf('/');
            if (url.charAt(index - 1) != '/') valid.set(url.substring(0, index + 1) + link);
            else valid.set(url + '/' + link);
        }
        else if (no_protocol_matcher.matches()) valid.set(protocol + ':' + link);
        else if (nosource_slash_matcher.matches()) {
            final var index = url.lastIndexOf('/');
            if (url.charAt(index - 1) != '/') valid.set(url.substring(0, index) + link);
            else valid.set(url + link);
        }
        return valid.get();
    }

    /**
     * Helper method for checking redundancy when submitting work to the worker threads.
     *
     * @param url the url to connect to
     * @param doc the Document to establish a connection for
     * @return whether the url is redundant work or not
     * @throws IOException if there was a problem connecting to the url
     */
    private boolean isRedundant(String url, AtomicReference<Document> doc) throws IOException {
        doc.set(Jsoup.connect(url).userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:63.0) Gecko/20100101 Firefox/63.0").get());
        // If the Task is not set to die, and the url has not been crawled
        // yet, add it to the list of crawled_pages as it will be crawled,
        // otherwise mark the url as redundant as it has already been seen
        if (!kill_all && !crawled_pages.containsKey(url)) {
            crawled_pages.put(url, doc.get().title());
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
     * the current worker Task, effectively stopping worker Task submissions.
     *
     * @param doc document connected to url
     * @param url url used to connect to doc
     * @param protocol protocol of url
     * @param depth depth of the current url
     */
    private void crawl(AtomicReference<Document> doc, String url, String protocol, Integer depth) throws RejectedExecutionException {
        // Obtain all the href attributes in anchor tags found in the url's html
        final var links = doc.get().select("a[href]");
        for (var link : links) {
            // Obtain the actual href value from this link's anchor tag
            final var href_matcher = href_pattern.matcher(link.toString());
            if (href_matcher.matches()) {
                if (kill_all) break;
                // Validate the matched href value and store it
                final var valid = validateLink(href_matcher.group(2), url, protocol);
                // Create the appropriate worker task for this valid link based on window input
                if (depth == null) workers.get().submit(new Task(valid));
                else if (depth + 1 == max_depth) {
                    try {
                        // The list of crawled_pages is checked to see if the current
                        // link has been crawled already, if so it is skipped; if it is
                        // not contained in the list, we connect to and add it
                        isRedundant(valid, doc);
                    }
                    // Ignore validated links that fail
                    catch (IOException ignored) {}
                }
                else workers.get().submit(new Task(valid, depth + 1));
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
            // Periodically make checks to see if the thread should be killed
            if (!kill_all) {
                try {
                    final var protocol = url.substring(0, url.indexOf("://"));
                    final var doc = new AtomicReference<Document>();
                    // If redundant skip the url
                    if (!isRedundant(url, doc)) crawl(doc, url, protocol, depth);
                }
                catch (IOException | IndexOutOfBoundsException ignored) {}
                // If this Task is the only task running at this point and there is no
                // more work to be done, the UI updates to reflect the completion
                if (workers.get().getActiveCount() == 1 && workers.get().getQueue().size() == 0) {
                    if (time_limit_toggle.isSelected()) timer.get().stop();
                    if (run_button.isSelected()){
                        run_button.setText("Run");
                        run_button.setSelected(false);
                    }
                    else run_button.setText("Run");
                }
            }
        }
    }
}
