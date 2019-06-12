package crawler;

import javax.net.ssl.HttpsURLConnection;
import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public class WebCrawler extends JFrame {

    private final Pattern title_pattern = Pattern.compile("(?Uis).*?<title[^>]*>(.*?)(?:</title>.*?)?");
    private final Pattern charset_pattern = Pattern.compile("(?Ui)^.*?charset=([\\w-]+).*$");
    private final Pattern href_pattern = Pattern.compile("(?Ui).*?<a\\s(?:[^>]+//s)?href=([\"'])([^\"']+)\\1[^>]*>(?:.+?</a>)?.*?");
    private final Pattern relative_pattern = Pattern.compile("(?Ui)^([^/]+)");
    private final Pattern no_protocol_pattern = Pattern.compile("(?Ui)^//.+");
    private final Pattern nosource_slash_pattern = Pattern.compile("(?Ui)^/[^/]+.*");
    private final Pattern nosource_noslash_pattern = Pattern.compile("(?Ui)^[^/]+.+/[^/]*");
    private volatile AtomicReference<ThreadPoolExecutor> workers = new AtomicReference<>();
    private volatile boolean kill_all = false;
    private volatile boolean waiting = true;
    private volatile JLabel parsed_pages_updater = new JLabel("0");
    private volatile Hashtable<String, String> crawled_pages = new Hashtable<>();
    private volatile ArrayBlockingQueue<Runnable> tasks = new ArrayBlockingQueue<>(10000);

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
        final var time_limit_toggle = new JCheckBox("Enabled");
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

        run(run_button, url_text, workers_text, depth_text, depth_toggle,
                time_limit_text, time_limit_toggle, elapsed_time_updater);
        save(export_text, save_button);
    }

    private void run(JToggleButton run_button, JTextField url_text, JTextField workers_text, JTextField depth_text, JCheckBox depth_toggle,
                     JTextField time_limit_text, JCheckBox time_limit_toggle, JLabel elapsed_time_updater) {
        HttpsURLConnection.setDefaultHostnameVerifier((hostname, sslSession) -> hostname.equals("localhost"));
        final var timer_worker = Executors.newSingleThreadExecutor();
        final var parser_worker = Executors.newSingleThreadExecutor();
        final var formatter = new SimpleDateFormat("m:ss");
        final var time = new AtomicLong(1000L);
        final var timer = new AtomicReference<Timer>();
        final var max_depth = new AtomicInteger(1);
        final var worker_count = new AtomicInteger(0);
        run_button.addItemListener(e -> {
            if (run_button.getText().equals("Run") && run_button.isSelected()) {
                run_button.setText("Stop");
                run_button.setSelected(false);
                kill_all = false;
                if (!crawled_pages.isEmpty()) crawled_pages.clear();
                elapsed_time_updater.setText("0:00");
                parsed_pages_updater.setText("0");
                if (time_limit_toggle.isSelected()) {
                    time.set(1000L);
                    if (timer.get() == null) {
                        timer.set(new Timer(1000, evt -> {
                            if (time.get() >= Long.parseLong(time_limit_text.getText()) * 1000L) {
                                kill_all = true;
                                ((Timer)evt.getSource()).stop();
                            }
                            elapsed_time_updater.setText(formatter.format(time.get()));
                            time.set(time.get() + 1000L);
                        }));
                    }
                    else timer.get().stop();
                    timer_worker.submit(() -> {
                        timer.get().restart();
                        return null;
                    });
                }
                if (depth_toggle.isSelected()) max_depth.set(Integer.parseInt(depth_text.getText()));
                try {
                    parser_worker.submit(() -> {
                        worker_count.set(Integer.parseInt(workers_text.getText()));
                        if (workers.get() == null) workers.set((ThreadPoolExecutor)Executors.newFixedThreadPool(worker_count.get()));
                        else if (workers.get().getCorePoolSize() != worker_count.get()) {
                            if (worker_count.get() > workers.get().getMaximumPoolSize()) workers.get().setMaximumPoolSize(worker_count.get());
                            workers.get().setCorePoolSize(worker_count.get());
                        }
                        workers.get().submit(new Task(url_text.getText()));
                        waiting = true;
                        if (!tasks.isEmpty()) tasks.clear();
                        while (!tasks.isEmpty() || workers.get().getActiveCount() != 0 || waiting) {
                            if (kill_all) {
                                workers.get().getQueue().clear();
                                tasks.clear();
                                break;
                            }
                            if (!tasks.isEmpty()) workers.get().submit(tasks.remove());
                        }
                        if (time_limit_toggle.isSelected()) timer.get().stop();
                        run_button.setText("Run");
                        return null;
                    });
                }
                catch (Exception error) {
                    // TODO add error popup window
                    error.printStackTrace();
                }
            }
            else if (run_button.getText().equals("Stop") && run_button.isSelected()) {
                kill_all = true;
                if (workers.get() != null) {
                    if (workers.get().getQueue() != null) workers.get().getQueue().clear();
                }
                tasks.clear();
                if (timer.get() != null && time_limit_toggle.isSelected()) timer.get().stop();
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
            }
            catch (IOException error) {
                // TODO add error popup window
                error.printStackTrace();
            }
        });
    }

    private String getLinkTitle(Object[] lines) {
        StringBuilder nextLine;
        final var i = new AtomicInteger(0);
        while (i.get() < lines.length) {
            nextLine = new StringBuilder((String)lines[i.getAndIncrement()]);
            final var title_matcher = new AtomicReference<>(title_pattern.matcher(nextLine));
            if (title_matcher.get().matches()) {
                while (!nextLine.toString().toLowerCase().contains("</title>")) {
                    final var next = (String)lines[i.getAndIncrement()];
                    nextLine.append(next);
                }
                title_matcher.set(title_pattern.matcher(nextLine));
                if (title_matcher.get().matches()) return title_matcher.get().group(1);
            }
        }
        return "";
    }

    private String validateLink(AtomicReference<String> link, String url, String protocol) {
        final var relative_matcher = relative_pattern.matcher(link.get());
        final var no_protocol_matcher = no_protocol_pattern.matcher(link.get());
        final var nosource_slash_matcher = nosource_slash_pattern.matcher(link.get());
        final var nosource_noslash_matcher = nosource_noslash_pattern.matcher(link.get());
        if (relative_matcher.matches() || (nosource_noslash_matcher.matches() && !link.get().contains("://"))) {
            var index = url.lastIndexOf('/');
            if (url.charAt(index - 1) != '/') link.set(url.substring(0, index + 1) + link.get());
            else link.set(url + '/' + link.get());
        }
        else if (no_protocol_matcher.matches()) link.set(protocol + ':' + link.get());
        else if (nosource_slash_matcher.matches()) {
            final var index = url.lastIndexOf('/');
            if (url.charAt(index - 1) != '/') link.set(url.substring(0, index) + link.get());
            else link.set(url + link.get());
        }
        return link.get();
    }

    private Charset getCharSet(String match) {
        switch (match.toLowerCase()) {
            case "us-ascii":
                return StandardCharsets.US_ASCII;
            case "iso-8859-1":
                return StandardCharsets.ISO_8859_1;
            case "utf-16":
                return StandardCharsets.UTF_16;
            case "utf-16be":
                return StandardCharsets.UTF_16BE;
            case "utf-16le":
                return StandardCharsets.UTF_16LE;
        }
        return StandardCharsets.UTF_8;
    }

    class Task implements Runnable {

        private String url;

        private Task(String url) {
            this.url = url;
        }

        @Override
        public void run() {
            try {
                System.out.println(Thread.currentThread().getName() + " started => " + url);
                final var input = new URI(url).toURL();
                final var protocol = new AtomicReference<>(input.getProtocol());
                final var input_stream = input.openConnection();
                input_stream.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:63.0) Gecko/20100101 Firefox/63.0");
                final var charset_matcher = new AtomicReference<>(charset_pattern.matcher(input_stream.getContentType()));
                final var charset = new AtomicReference<>(StandardCharsets.UTF_8);
                if (charset_matcher.get().matches()) charset.set(getCharSet(charset_matcher.get().group(1)));
                final var buffered_input_stream = new BufferedReader(new InputStreamReader(input_stream.getInputStream(), charset.get()));
                final var buffered_lines = buffered_input_stream.lines().toArray();

                final var i = new AtomicInteger(0);
                while (i.get() < buffered_lines.length) {
                    final var html = new StringBuilder(buffered_lines[i.get()].toString());
                    final var title_matcher = new AtomicReference<>(title_pattern.matcher(buffered_lines[i.get()].toString()));
                    if (title_matcher.get().matches() && crawled_pages.isEmpty()) {
                        while (!html.toString().contains("</title>")) {
                            html.append(buffered_lines[i.getAndIncrement()].toString());
                        }
                        title_matcher.set(title_pattern.matcher(html.toString()));
                        if (title_matcher.get().matches()) {
                            crawled_pages.put(url, title_matcher.get().group(1));
                            parsed_pages_updater.setText(String.valueOf(crawled_pages.size()));
                        }
                    }
                    final var href_matcher = href_pattern.matcher(html);
                    while (!kill_all && href_matcher.find()) {
                        final var link = new AtomicReference<>(href_matcher.group(2));
                        final var connection = new AtomicReference<URLConnection>();
                        link.set(validateLink(link, url, protocol.get()));
                        final var original_link = link.get();
                        try {
                            connection.set(new URI(link.get()).toURL().openConnection());
                            connection.get().setConnectTimeout(1000);
                            connection.get().setReadTimeout(2000);
                            final var content = new AtomicReference<>(connection.get().getContentType());
                            if (content.get().contains("text/html")) {
                                charset_matcher.set(charset_pattern.matcher(content.get()));
                                if (charset_matcher.get().matches()) charset.set(getCharSet(charset_matcher.get().group(1)));
                                else charset.set(StandardCharsets.UTF_8);
                                if (!kill_all && !crawled_pages.containsKey(link.get())) {
                                    final var buffered_link_input_stream = new BufferedReader(new InputStreamReader(connection.get().getInputStream(), charset.get()));
                                    final var lines = buffered_link_input_stream.lines().toArray();
                                    final var count = lines.length;
                                    if (count == 0) {
                                        buffered_link_input_stream.close();
                                        throw new IOException();
                                    }
                                    final var title = getLinkTitle(lines);
                                    if (title.equals("")) throw new IOException();
                                    crawled_pages.put(link.get(), title);
                                    tasks.put(new Task(link.get()));
                                    if (waiting) waiting = false;
                                    buffered_link_input_stream.close();
                                    parsed_pages_updater.setText(String.valueOf(crawled_pages.size()));
                                }
                            }
                        }
                        catch (IOException | IllegalArgumentException | URISyntaxException | NullPointerException change_protocol) {
                            try {
                                if (link.get().substring(0, link.get().indexOf("://")).equals("http")) link.set("https" + link.get().substring(4));
                                else link.set("http" + link.get().substring(5));
                                connection.set(new URI(link.get()).toURL().openConnection());
                                connection.get().setConnectTimeout(1000);
                                connection.get().setReadTimeout(2000);
                                final var content = new AtomicReference<>(connection.get().getContentType());
                                if (content.get().contains("text/html")) {
                                    charset_matcher.set(charset_pattern.matcher(content.get()));
                                    if (charset_matcher.get().matches()) charset.set(getCharSet(charset_matcher.get().group(1)));
                                    else charset.set(StandardCharsets.UTF_8);
                                    if (!kill_all && !crawled_pages.containsKey(link.get())) {
                                        final var buffered_link_input_stream = new BufferedReader(new InputStreamReader(connection.get().getInputStream(), charset.get()));
                                        final var lines = buffered_link_input_stream.lines().toArray();
                                        final var count = lines.length;
                                        if (count == 0) {
                                            buffered_link_input_stream.close();
                                            throw new IOException();
                                        }
                                        final var title = getLinkTitle(lines);
                                        if (title.equals("")) throw new IOException();
                                        crawled_pages.put(link.get(), title);
                                        tasks.put(new Task(link.get()));
                                        if (waiting) waiting = false;
                                        buffered_link_input_stream.close();
                                        parsed_pages_updater.setText(String.valueOf(crawled_pages.size()));
                                    }
                                }
                            }
                            catch (IOException | IllegalArgumentException | URISyntaxException | NullPointerException add_www) {
                                try {
                                    if (link.get().substring(0, link.get().indexOf("://")).equals("http")) link.set("https" + link.get().substring(4));
                                    else link.set("http" + link.get().substring(5));
                                    if (link.get().contains("://www.")) throw new IOException();
                                    link.set(link.get().substring(0, link.get().indexOf("://") + 3) + "www." + link.get().substring(link.get().indexOf("://") + 3));
                                    connection.set(new URI(link.get()).toURL().openConnection());
                                    connection.get().setConnectTimeout(1000);
                                    connection.get().setReadTimeout(2000);
                                    final var content = new AtomicReference<>(connection.get().getContentType());
                                    if (content.get().contains("text/html")) {
                                        charset_matcher.set(charset_pattern.matcher(content.get()));
                                        if (charset_matcher.get().matches()) charset.set(getCharSet(charset_matcher.get().group(1)));
                                        else charset.set(StandardCharsets.UTF_8);
                                        if (!kill_all && !crawled_pages.containsKey(link.get())) {
                                            final var buffered_link_input_stream = new BufferedReader(new InputStreamReader(connection.get().getInputStream(), charset.get()));
                                            final var lines = buffered_link_input_stream.lines().toArray();
                                            final var count = lines.length;
                                            if (count == 0) {
                                                buffered_link_input_stream.close();
                                                throw new IOException();
                                            }
                                            final var title = getLinkTitle(lines);
                                            if (title.equals("")) throw new IOException();
                                            crawled_pages.put(link.get(), title);
                                            tasks.put(new Task(link.get()));
                                            if (waiting) waiting = false;
                                            buffered_link_input_stream.close();
                                            parsed_pages_updater.setText(String.valueOf(crawled_pages.size()));
                                        }
                                    }
                                }
                                catch (IOException | IllegalArgumentException | URISyntaxException | NullPointerException change_protocol_add_www) {
                                    try {
                                        if (link.get().substring(0, link.get().indexOf("://")).equals("http")) link.set("https" + link.get().substring(4));
                                        else link.set("http" + link.get().substring(5));
                                        connection.set(new URI(link.get()).toURL().openConnection());
                                        connection.get().setConnectTimeout(1000);
                                        connection.get().setReadTimeout(2000);
                                        final var content = new AtomicReference<>(connection.get().getContentType());
                                        if (content.get().contains("text/html")) {
                                            charset_matcher.set(charset_pattern.matcher(content.get()));
                                            if (charset_matcher.get().matches()) charset.set(getCharSet(charset_matcher.get().group(1)));
                                            else charset.set(StandardCharsets.UTF_8);
                                            if (!kill_all && !crawled_pages.containsKey(link.get())) {
                                                final var buffered_link_input_stream = new BufferedReader(new InputStreamReader(connection.get().getInputStream(), charset.get()));
                                                final var lines = buffered_link_input_stream.lines().toArray();
                                                final var count = lines.length;
                                                if (count == 0) {
                                                    buffered_link_input_stream.close();
                                                    throw new IOException();
                                                }
                                                final var title = getLinkTitle(lines);
                                                if (title.equals("")) crawled_pages.put(original_link, title);
                                                else crawled_pages.put(link.get(), title);
                                                tasks.put(new Task(link.get()));
                                                if (waiting) waiting = false;
                                                buffered_link_input_stream.close();
                                                parsed_pages_updater.setText(String.valueOf(crawled_pages.size()));
                                            }
                                        }
                                    }
                                    catch (IOException | IllegalArgumentException | URISyntaxException | NullPointerException ignored) {}
                                }
                            }
                            catch (IndexOutOfBoundsException ignored) {}
                        }

                    }
                    i.getAndIncrement();
                }
            }
            catch (IOException | URISyntaxException e) {
                e.printStackTrace();
            }
            catch (InterruptedException ignored) {
                kill_all = true;
                if (workers.get() != null) workers.get().getQueue().clear();
                tasks.clear();
            }
            System.out.println(Thread.currentThread().getName() + " finished => " + url);
        }
    }
}
