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
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;

public class WebCrawler extends JFrame {

    private final Pattern href_pattern = Pattern.compile("(?Uis).*href=(['\"])(.*?)\\1.*");
    private final Pattern relative_pattern = Pattern.compile("(?Ui)^([^/]+)");
    private final Pattern no_protocol_pattern = Pattern.compile("(?Ui)^//.+");
    private final Pattern nosource_slash_pattern = Pattern.compile("(?Ui)^/[^/]+.*");
    private final Pattern nosource_noslash_pattern = Pattern.compile("(?Ui)^[^/]+.+/[^/]*");
    private final JLabel parsed_pages_updater = new JLabel("0");
    private volatile boolean waiting = true;
    private volatile boolean kill_all = false;
    private volatile Hashtable<String, String> crawled_pages = new Hashtable<>();
    private volatile AtomicReference<ThreadPoolExecutor> workers = new AtomicReference<>();
    private final AtomicInteger worker_count = new AtomicInteger(0);
    private final AtomicReference<Timer> timer = new AtomicReference<>();
    private final JToggleButton run_button = new JToggleButton("Run");
    private final JCheckBox time_limit_toggle = new JCheckBox("Enabled");
    private int max_depth = 0;


    public WebCrawler() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        setTitle("Web Crawler");
        setVisible(true);

        final var url_label = new JLabel("Start URL: ");
        final var url_text = new JTextField();
        url_text.setName("UrlTextField");
        final var run_button = new JToggleButton("Run");
        run_button.setName("RunButton");
        final var workers_label = new JLabel("Workers: ");
        final var workers_text = new JTextField();
        final var depth_label = new JLabel("Maximum depth:  ");
        final var depth_text = new JTextField();
        depth_text.setName("DepthTextField");
        final var depth_toggle = new JCheckBox("Enabled");
        depth_toggle.setName("DepthCheckBox");
        final var time_limit_label = new JLabel("Time limit: ");
        final var time_limit_text = new JTextField();
        final var seconds_label = new JLabel("seconds");
        final var elapsed_time_label = new JLabel("Elapsed time: ");
        final var elapsed_time_updater = new JLabel("0:00");
        final var parsed_pages_label = new JLabel("Parsed pages: ");
        parsed_pages_updater.setName("ParsedLabel");
        final var export_label = new JLabel("Export: ");
        final var export_text = new JTextField();
        export_text.setName("ExportUrlTextField");
        final var save_button = new JButton("Save");
        save_button.setName("ExportButton");

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
        setContentPane(input_pane);
        setSize(500, 250);

        run(url_text, workers_text, depth_text, depth_toggle, time_limit_text, elapsed_time_updater);
        save(export_text, save_button);
    }

    private void run(JTextField url_text, JTextField workers_text, JTextField depth_text, JCheckBox depth_toggle, JTextField time_limit_text, JLabel elapsed_time_updater) {
        final var formatter = new SimpleDateFormat("m:ss");
        final var time = new AtomicLong(1000L);
        run_button.addItemListener(e -> {
            if (run_button.getText().equals("Run") && run_button.isSelected()) {
                run_button.setText("Stop");
                run_button.setSelected(false);
                elapsed_time_updater.setText("0:00");
                parsed_pages_updater.setText("0");
                if (depth_toggle.isSelected()) max_depth = Integer.parseInt(depth_text.getText());
                try {
                    worker_count.set(Integer.parseInt(workers_text.getText()));
                    if (workers.get() == null) workers.set((ThreadPoolExecutor) Executors.newFixedThreadPool(worker_count.get()));
                    else if (workers.get().getCorePoolSize() != worker_count.get()) {
                        if (worker_count.get() > workers.get().getMaximumPoolSize()) workers.get().setMaximumPoolSize(worker_count.get());
                        workers.get().setCorePoolSize(worker_count.get());
                    }
                    kill_all = false;
                    waiting = true;
                    crawled_pages.clear();
                    if (depth_toggle.isSelected()) workers.get().submit(new Task(url_text.getText(), 0));
                    else workers.get().submit(new Task(url_text.getText()));
                    if (time_limit_toggle.isSelected()) {
                        time.set(1000L);
                        if (timer.get() == null) {
                            timer.set(new Timer(1000, evt -> {
                                if (time.get() >= Long.parseLong(time_limit_text.getText()) * 1000L && !waiting) {
                                    kill_all = true;
                                    run_button.setText("Run");
                                    run_button.setSelected(false);
                                    ((Timer) evt.getSource()).stop();
                                }
                                elapsed_time_updater.setText(formatter.format(time.get()));
                                time.set(time.get() + 1000L);
                            }));
                        }
                        timer.get().restart();
                    }
                }
                catch (IllegalArgumentException | RejectedExecutionException error) {
                    kill_all = true;
                    if (time_limit_toggle.isSelected()) timer.get().stop();
                    run_button.setText("Run");
                    run_button.setSelected(false);
                }
            }
            else if (run_button.getText().equals("Stop") && run_button.isSelected()) {
                kill_all = true;
                if (time_limit_toggle.isSelected()) timer.get().stop();
                run_button.setText("Run");
                run_button.setSelected(false);
            }
        });
    }

    private void save(JTextField export_text, JButton export_button) {
        export_button.addActionListener(e -> {
            export_button.setSelected(false);
            try {
                final var output = new OutputStreamWriter(new FileOutputStream(export_text.getText()), StandardCharsets.UTF_8);
                for (var entry : crawled_pages.entrySet()) {
                    output.write(entry.getKey() + "\n");
                    output.write(entry.getValue() + "\n");
                }
                output.close();
            } catch (IOException error) {
                // TODO add error popup window
                error.printStackTrace();
            }
        });
    }

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
        } else if (no_protocol_matcher.matches()) valid.set(protocol + ':' + link);
        else if (nosource_slash_matcher.matches()) {
            final var index = url.lastIndexOf('/');
            if (url.charAt(index - 1) != '/') valid.set(url.substring(0, index) + link);
            else valid.set(url + link);
        }
        return valid.get();
    }

    class Task implements Runnable {

        private String url;
        private final Integer depth;

        private Task(String url, Integer depth) {
            this.url = url;
            this.depth = depth;
        }

        private Task(String url) {
            this(url, null);
        }

        @Override
        public void run() {
            System.out.println("started");
            if (!kill_all) {
                final var protocol = url.substring(0, url.indexOf("://"));
                try {
                    final var doc = Jsoup.connect(url).userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:63.0) Gecko/20100101 Firefox/63.0").get();
                    if (!kill_all) {
                        crawled_pages.put(url, doc.title());
                        parsed_pages_updater.setText(String.valueOf(crawled_pages.size()));
                    }
                    if (!crawled_pages.containsKey(url) && !kill_all) {
                        if (depth == null) {
                            final var links = doc.select("a[href]");
                            if (links.isEmpty() && crawled_pages.size() == 1) {
                                kill_all = true;
                                waiting = false;
                                if (time_limit_toggle.isSelected()) timer.get().stop();
                                run_button.setText("Run");
                                run_button.setSelected(false);
                            }
                            for (var link : links) {
                                final var href_matcher = href_pattern.matcher(link.toString());
                                if (href_matcher.matches()) {
                                    if (kill_all) break;
                                    if (!crawled_pages.containsKey(href_matcher.group(2))) {
                                        final var valid = validateLink(href_matcher.group(2), url, protocol);
                                        workers.get().submit(new Task(valid));
                                        System.out.println(valid);
                                    }
                                    if (waiting) waiting = false;
                                }
                            }
                        }
                        else if (depth + 1 <= max_depth) {
                            final var links = doc.select("a[href]");
                            if (links.isEmpty() && crawled_pages.size() == 1) {
                                kill_all = true;
                                waiting = false;
                                if (time_limit_toggle.isSelected()) timer.get().stop();
                                run_button.setText("Run");
                                run_button.setSelected(false);
                            }
                            for (var link : links) {
                                final var href_matcher = href_pattern.matcher(link.toString());
                                if (href_matcher.matches()) {
                                    if (kill_all) break;
                                    if (!crawled_pages.containsKey(href_matcher.group(2))) {
                                        final var valid = validateLink(href_matcher.group(2), url, protocol);
                                        workers.get().submit(new Task(valid, depth + 1));
                                        System.out.println(valid);
                                    }
                                    if (waiting) waiting = false;
                                }
                            }
                        }
                    }
                }
                catch (IOException e) {
                    if (e instanceof HttpStatusException) {
                        final var status = ((HttpStatusException) e).getStatusCode();
                        if (status == 403) {
                            if (protocol.equals("http")) {
                                url = "https" + url.substring(url.indexOf("://"));
                                try {
                                    final var doc = Jsoup.connect(url).userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:63.0) Gecko/20100101 Firefox/63.0").get();
                                    if (!kill_all) {
                                        crawled_pages.put(url, doc.title());
                                        parsed_pages_updater.setText(String.valueOf(crawled_pages.size()));
                                    }
                                    if (!crawled_pages.containsKey(url) && !kill_all) {
                                        if (depth == null) {
                                            final var links = doc.select("a[href]");
                                            if (links.isEmpty() && crawled_pages.size() == 1) {
                                                kill_all = true;
                                                waiting = false;
                                                if (time_limit_toggle.isSelected()) timer.get().stop();
                                                run_button.setText("Run");
                                                run_button.setSelected(false);
                                            }
                                            for (var link : links) {
                                                final var href_matcher = href_pattern.matcher(link.toString());
                                                if (href_matcher.matches()) {
                                                    if (kill_all) break;
                                                    if (!crawled_pages.containsKey(href_matcher.group(2))) {
                                                        final var valid = validateLink(href_matcher.group(2), url, protocol);
                                                        workers.get().submit(new Task(valid));
                                                        System.out.println(valid);
                                                    }
                                                    if (waiting) waiting = false;
                                                }
                                            }
                                        }
                                        else if (depth + 1 <= max_depth) {
                                            final var links = doc.select("a[href]");
                                            if (links.isEmpty() && crawled_pages.size() == 1) {
                                                kill_all = true;
                                                waiting = false;
                                                if (time_limit_toggle.isSelected()) timer.get().stop();
                                                run_button.setText("Run");
                                                run_button.setSelected(false);
                                            }
                                            for (var link : links) {
                                                final var href_matcher = href_pattern.matcher(link.toString());
                                                if (href_matcher.matches()) {
                                                    if (kill_all) break;
                                                    if (!crawled_pages.containsKey(href_matcher.group(2))) {
                                                        final var valid = validateLink(href_matcher.group(2), url, protocol);
                                                        workers.get().submit(new Task(valid, depth + 1));
                                                        System.out.println(valid);
                                                    }
                                                    if (waiting) waiting = false;
                                                }
                                            }
                                        }
                                    }
                                }
                                catch (IOException ignored) {}
                            }
                        }
                    }
                }
            }
            System.out.println("stopped");
        }
    }
}

