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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

class WebCrawler {

    private final Pattern title_pattern = Pattern.compile("(?Uis).*?<title[^>]*>(.*?)(?:</title>.*?)?");
    private final Pattern charset_pattern = Pattern.compile("(?Ui)^.*?charset=([\\w-]+).*$");
    private final Pattern href_pattern = Pattern.compile("(?Ui).*?<a\\s(?:[^>]+//s)?href=([\"'])([^\"']+)\\1[^>]*>(?:.+?</a>)?.*?");
    private final Pattern relative_pattern = Pattern.compile("(?Ui)^([^/]+)");
    private final Pattern no_protocol_pattern = Pattern.compile("(?Ui)^//.+");
    private final Pattern nosource_slash_pattern = Pattern.compile("(?Ui)^/[^/]+.*");
    private final Pattern nosource_noslash_pattern = Pattern.compile("(?Ui)^[^/]+.+/[^/]*");
    private volatile Hashtable<String, String> crawled_pages = new Hashtable<>();
    private volatile ArrayList<String> queued_pages = new ArrayList<>();
    private volatile ParserThread[] workers;
    // TODO add a priority queue of unvisited, but crawled links

    WebCrawler() {
        final var frame = new JFrame();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.setTitle("Web Crawler");
        frame.setVisible(true);

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
        final var parsed_pages_updater = new JLabel("0");
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
        frame.setContentPane(input_pane);
        frame.setSize(500, 250);

        run(run_button, url_text, depth_text, depth_toggle, time_limit_text,
                time_limit_toggle, elapsed_time_updater, parsed_pages_updater);
        save(export_text, save_button);
    }

    private void run(JToggleButton run_button, JTextField url_text, JTextField depth_text, JCheckBox depth_toggle, JTextField time_limit_text,
                     JCheckBox time_limit_toggle, JLabel elapsed_time_updater, JLabel parsed_pages_updater) {
        HttpsURLConnection.setDefaultHostnameVerifier((hostname, sslSession) -> hostname.equals("localhost"));
        run_button.addActionListener(e -> {
            if (!crawled_pages.isEmpty()) crawled_pages.clear();
            elapsed_time_updater.setText("0:00");
            parsed_pages_updater.setText("0");
            final var buffered_input_stream = new AtomicReference<BufferedReader>();
            final var timer_worker = new AtomicReference<SwingWorker<String, Object>>();
            final var parser_worker = new AtomicReference<SwingWorker<String, Object>>();
            final var formatter = new SimpleDateFormat("m:ss");
            final var time = new AtomicLong(1000L);
            final var timer = new Timer(1000, evt -> {
                if (time.get() >= Long.parseLong(time_limit_text.getText()) * 1000L) {
                    ((Timer)evt.getSource()).stop();
                    parser_worker.get().cancel(true);
                }
                elapsed_time_updater.setText(formatter.format(time.get()));
                time.set(time.get() + 1000L);
            });
            if (time_limit_toggle.isSelected()) {
                timer_worker.set(new SwingWorker<>() {

                    @Override
                    protected String doInBackground() {
                        timer.start();
                        return null;
                    }
                });
                timer_worker.get().execute();
            }
            final var max_depth = new AtomicInteger();
            if (depth_toggle.isSelected()) max_depth.set(Integer.parseInt(depth_text.getText()));
            try {
                parser_worker.set(new SwingWorker<>() {

                    @Override
                    protected String doInBackground() throws Exception {
                        final var url = url_text.getText();
                        final var input = new URI(url).toURL();
                        final var protocol = new AtomicReference<>(input.getProtocol());
                        final var input_stream = input.openConnection();
                        input_stream.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:63.0) Gecko/20100101 Firefox/63.0");
                        final var charset_matcher = new AtomicReference<>(charset_pattern.matcher(input_stream.getContentType()));
                        final var charset = new AtomicReference<>(StandardCharsets.UTF_8);
                        if (charset_matcher.get().matches()) charset.set(getCharSet(charset_matcher.get().group(1)));
                        buffered_input_stream.set(new BufferedReader(new InputStreamReader(input_stream.getInputStream(), charset.get())));
                        final var buffered_lines = buffered_input_stream.get().lines().toArray();

                        final var i = new AtomicInteger(0);
                        while (i.get() < buffered_lines.length) {
                            final var html = new StringBuilder(buffered_lines[i.get()].toString());
                            final var title_matcher = new AtomicReference<>(title_pattern.matcher(buffered_lines[i.get()].toString()));
                            if (title_matcher.get().matches()) {
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
                            while (href_matcher.find() && !isCancelled()) {
                                final var link = new AtomicReference<>(href_matcher.group(2));
                                final var connection = new AtomicReference<URLConnection>();
                                link.set(validateLink(link, url, protocol.get()));
                                try {
                                    connection.set(new URI(link.get()).toURL().openConnection());
                                    connection.get().setConnectTimeout(1000);
                                    connection.get().setReadTimeout(1000);
                                    final var content = new AtomicReference<>(connection.get().getContentType());
                                    if (content.get().contains("text/html")) {
                                        charset_matcher.set(charset_pattern.matcher(content.get()));
                                        if (charset_matcher.get().matches()) charset.set(getCharSet(charset_matcher.get().group(1)));
                                        else charset.set(StandardCharsets.UTF_8);
                                        // TODO make sure the link hasn't been seen already
                                        if (!isCancelled() && !crawled_pages.containsKey(link.get())) {
                                            final var buffered_link_input_stream = new BufferedReader(new InputStreamReader(connection.get().getInputStream(), charset.get()));
                                            final var lines = buffered_link_input_stream.lines().toArray();
                                            final var count = lines.length;
                                            if (count == 0) {
                                                buffered_link_input_stream.close();
                                                throw new IOException();
                                            }
                                            crawled_pages.put(link.get(), getLinkTitle(lines));
                                            buffered_link_input_stream.close();
                                            parsed_pages_updater.setText(String.valueOf(crawled_pages.size()));
                                        }
                                    }
                                }
                                catch (IOException | IllegalArgumentException | URISyntaxException | NullPointerException change_protocol) {
                                    try {
                                        if (link.get().substring(0, link.get().indexOf("://")).equals("https")) protocol.set("http");
                                        else protocol.set("https");
                                        link.set(protocol + link.get().substring(link.get().indexOf("://")));
                                        connection.set(new URI(link.get()).toURL().openConnection());
                                        connection.get().setConnectTimeout(1000);
                                        connection.get().setReadTimeout(1000);
                                        final var content = new AtomicReference<>(connection.get().getContentType());
                                        if (content.get().contains("text/html")) {
                                            charset_matcher.set(charset_pattern.matcher(content.get()));
                                            if (charset_matcher.get().matches()) charset.set(getCharSet(charset_matcher.get().group(1)));
                                            else charset.set(StandardCharsets.UTF_8);
                                            // TODO make sure the link hasn't been seen already
                                            if (!isCancelled() && !crawled_pages.containsKey(link.get())) {
                                                final var buffered_link_input_stream = new BufferedReader(new InputStreamReader(connection.get().getInputStream(), charset.get()));
                                                final var lines = buffered_link_input_stream.lines().toArray();
                                                final var count = lines.length;
                                                if (count == 0) {
                                                    buffered_link_input_stream.close();
                                                    throw new IOException();
                                                }
                                                crawled_pages.put(link.get(), getLinkTitle(lines));
                                                buffered_link_input_stream.close();
                                                parsed_pages_updater.setText(String.valueOf(crawled_pages.size()));
                                            }
                                        }
                                    }
                                    catch (IOException | IllegalArgumentException | URISyntaxException | NullPointerException add_www) {
                                        try {
                                            if (link.get().substring(0, link.get().indexOf("://")).equals("https")) protocol.set("http");
                                            else protocol.set("https");
                                            link.set(protocol + link.get().substring(link.get().indexOf("://")));
                                            if (link.get().contains("://www.")) throw new IOException();
                                            link.set(link.get().substring(0, link.get().indexOf("://") + 3) + "www." + link.get().substring(link.get().indexOf("://") + 3));
                                            connection.set(new URI(link.get()).toURL().openConnection());
                                            connection.get().setConnectTimeout(1000);
                                            connection.get().setReadTimeout(1000);
                                            final var content = new AtomicReference<>(connection.get().getContentType());
                                            if (content.get().contains("text/html")) {
                                                charset_matcher.set(charset_pattern.matcher(content.get()));
                                                if (charset_matcher.get().matches()) charset.set(getCharSet(charset_matcher.get().group(1)));
                                                else charset.set(StandardCharsets.UTF_8);
                                                // TODO make sure the link hasn't been seen already
                                                if (!isCancelled() && !crawled_pages.containsKey(link.get())) {
                                                    final var buffered_link_input_stream = new BufferedReader(new InputStreamReader(connection.get().getInputStream(), charset.get()));
                                                    final var lines = buffered_link_input_stream.lines().toArray();
                                                    final var count = lines.length;
                                                    if (count == 0) {
                                                        buffered_link_input_stream.close();
                                                        throw new IOException();
                                                    }
                                                    crawled_pages.put(link.get(), getLinkTitle(lines));
                                                    buffered_link_input_stream.close();
                                                    parsed_pages_updater.setText(String.valueOf(crawled_pages.size()));
                                                }
                                            }
                                        }
                                        catch (IOException | IllegalArgumentException | URISyntaxException | NullPointerException change_protocol_add_www) {
                                            try {
                                                if (!link.get().contains("://www.")) link.set(link.get().substring(0, link.get().indexOf("://") + 3) + "www." + link.get().substring(link.get().indexOf("://") + 3));
                                                if (link.get().substring(0, link.get().indexOf("://")).equals("https")) protocol.set("http");
                                                else protocol.set("https");
                                                link.set(protocol + link.get().substring(link.get().indexOf("://")));
                                                connection.set(new URI(link.get()).toURL().openConnection());
                                                connection.get().setConnectTimeout(1000);
                                                connection.get().setReadTimeout(1000);
                                                final var content = new AtomicReference<>(connection.get().getContentType());
                                                if (content.get().contains("text/html")) {
                                                    charset_matcher.set(charset_pattern.matcher(content.get()));
                                                    if (charset_matcher.get().matches()) charset.set(getCharSet(charset_matcher.get().group(1)));
                                                    else charset.set(StandardCharsets.UTF_8);
                                                    // TODO make sure the link hasn't been seen already
                                                    if (!isCancelled() && !crawled_pages.containsKey(link.get())) {
                                                        final var buffered_link_input_stream = new BufferedReader(new InputStreamReader(connection.get().getInputStream(), charset.get()));
                                                        final var lines = buffered_link_input_stream.lines().toArray();
                                                        final var count = lines.length;
                                                        if (count == 0) {
                                                            buffered_link_input_stream.close();
                                                            throw new IOException();
                                                        }
                                                        crawled_pages.put(link.get(), getLinkTitle(lines));
                                                        buffered_link_input_stream.close();
                                                        parsed_pages_updater.setText(String.valueOf(crawled_pages.size()));
                                                    }
                                                }
                                            }
                                            catch (IOException | IllegalArgumentException | URISyntaxException | NullPointerException bad_url) {
                                                bad_url.printStackTrace();
                                            }
                                        }
                                    }
                                }
                            }
                            i.getAndIncrement();
                        }
                        if (time_limit_toggle.isSelected()) timer.stop();
                        return null;
                    }
                });
                parser_worker.get().execute();
            }
            catch (Exception error) {
                // TODO add error popup window
                error.printStackTrace();
            }
            finally {
                run_button.setSelected(false);
                try {
                    buffered_input_stream.get().close();
                }
                catch (IOException | NullPointerException ignored) {}
            }
        });
    }

    private void save(JTextField export_text, JButton export_button) {
        export_button.addActionListener(e -> {
            final var save_worker = new SwingWorker<String, Object>() {

                @Override
                public String doInBackground() {
                    try {
                        final var output = new OutputStreamWriter(new FileOutputStream(export_text.getText()), StandardCharsets.UTF_8);
                        for (var entry : crawled_pages.entrySet()) {
                            output.write(entry.getKey());
                            output.write("\n");
                            output.write(entry.getValue());
                            output.write("\n");
                        }
                        output.close();
                    }
                    catch (IOException error) {
                        // TODO add error popup window
                        error.printStackTrace();
                    }
                    return null;
                }
            };
            save_worker.execute();
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
        else if (no_protocol_matcher.matches()) {
            link.set(protocol + ':' + link.get());
        }
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

    public class ParserThread implements Runnable {

        private JTextField url_text;
        private JTextField depth_text;
        private JLabel elapsed_time_updater;
        private JLabel parsed_pages_updater;

        public ParserThread(JTextField url_text, JTextField depth_text, JLabel elapsed_time_updater, JLabel parsed_pages_updater) {
            this.url_text = url_text;
            this.depth_text = depth_text;
            this.elapsed_time_updater = elapsed_time_updater;
            this.parsed_pages_updater = parsed_pages_updater;
        }

        @Override
        public void run() {

        }
    }
}
