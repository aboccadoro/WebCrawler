package crawler;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebCrawler extends Thread {

    private final Pattern title_pattern = Pattern.compile("(?Uis).*?<title[^>]*>(.*?)(?:</title>.*?)?");
    private final Pattern charset_pattern = Pattern.compile("(?Ui)^.*?charset=([\\w-]+).*$");
    private final Pattern href_pattern = Pattern.compile("(?Ui).*?<a\\s(?:[^>]+//s)?href=([\"'])([^\"']+)\\1[^>]*>(?:.+?</a>)?.*?");
    private final Pattern relative_pattern = Pattern.compile("(?Ui)^([^/]+)");
    private final Pattern no_protocol_pattern = Pattern.compile("(?Ui)^//.+");
    private final Pattern nosource_slash_pattern = Pattern.compile("(?Ui)^/[^/]+.*");
    private final Pattern nosource_noslash_pattern = Pattern.compile("(?Ui)^[^/]+.+/[^/]*");

    public WebCrawler() {
        JFrame frame = new JFrame();
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
        final var parsed_page_label = new JLabel("Parsed pages: ");
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
                        .addComponent(true, run_button)
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
                                .addComponent(parsed_page_label)
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
                                .addComponent(parsed_page_label)
                                .addComponent(parsed_pages_updater)
                        )
                        .addGroup(input_layout.createParallelGroup(GroupLayout.Alignment.CENTER)
                                .addComponent(export_label)
                                .addComponent(export_pane)
                        )
        );
        frame.setContentPane(input_pane);

//        initParser(run_button, url_text, title_label_text, title_table_model);
//        initSave(export_text, export_button, title_table_model);

        frame.pack();
        frame.setSize(500, 250);
    }

    private void initParser(JButton run_button, JTextField url_text, JLabel title_label_text, DefaultTableModel title_table_model) {
        run_button.addActionListener(e -> {
            var buffered_input_stream = new AtomicReference<BufferedReader>();
            try {
                while (title_table_model.getRowCount() > 0) {
                    title_table_model.removeRow(title_table_model.getRowCount() - 1);
                }

                final var url = url_text.getText();
                final var input = new URI(url).toURL();
                var protocol = input.getProtocol();
                final var input_stream = input.openConnection();
                input_stream.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:63.0) Gecko/20100101 Firefox/63.0");
                var charset_matcher = new AtomicReference<>(charset_pattern.matcher(input_stream.getContentType()));
                var charset = new AtomicReference<>(StandardCharsets.UTF_8);
                if (charset_matcher.get().matches()) charset.set(getCharSet(charset_matcher.get().group(1)));
                buffered_input_stream.set(new BufferedReader(new InputStreamReader(input_stream.getInputStream(), charset.get())));
                var buffered_lines = buffered_input_stream.get().lines().toArray();

                var i = new AtomicInteger(0);
                while (i.get() < buffered_lines.length) {
                    var html = new StringBuilder(buffered_lines[i.get()].toString());
                    var title_matcher = new AtomicReference<>(title_pattern.matcher(buffered_lines[i.get()].toString()));
                    if (title_matcher.get().matches()) {
                        while (!html.toString().contains("</title>")) {
                            html.append(buffered_lines[i.getAndIncrement()].toString());
                        }
                        title_matcher.set(title_pattern.matcher(html.toString()));
                        if (title_matcher.get().matches()) title_label_text.setText(title_matcher.get().group(1));
                    }
                    var href_matcher = href_pattern.matcher(html);
                    while (href_matcher.find()) {
                        var link = new AtomicReference<>(href_matcher.group(2));
                        var validated = new AtomicBoolean(false);
                        var connection = new AtomicReference<URLConnection>();
                        try {
                            connection.set(new URI(link.get()).toURL().openConnection());
                        }
                        catch (IOException | IllegalArgumentException | URISyntaxException error) {
                            if (protocol.contains("http")) validateLink(link, url, protocol, validated);
                        }
                        finally {
                            try {
                                if (validated.get()) connection.set(new URI(link.get()).toURL().openConnection());
                                if (connection.get().getContentType().contains("text/html")) {
                                    charset_matcher.set(charset_pattern.matcher(connection.get().getContentType()));
                                    if (charset_matcher.get().matches()) charset.set(getCharSet(charset_matcher.get().group(1)));
                                    else charset.set(StandardCharsets.UTF_8);
                                    var buffered_link_input_stream = new BufferedReader(new InputStreamReader(connection.get().getURL().openStream(), charset.get()));
                                    var title = getLinkTitle(buffered_link_input_stream);
                                    String[] data = {link.get(), title};
                                    title_table_model.addRow(data);
                                    System.out.println(link.get() + "   |   " + title);
                                    buffered_link_input_stream.close();
                                }
                            }
                            catch (IOException | IllegalArgumentException | URISyntaxException | NullPointerException ignored) {}
                        }
                    }
                    i.getAndIncrement();
                }
            }
            catch (IOException | IllegalArgumentException | URISyntaxException error) {
                // TODO add error popup window
                error.printStackTrace();
            }
            finally {
                try {
                    if (buffered_input_stream.get() != null) buffered_input_stream.get().close();
                }
                catch (IOException ignored) {}
            }
        });
    }

    private void initSave(JTextField export_text, JButton export_button, DefaultTableModel title_table_model) {
        export_button.addActionListener(e -> {
            final var row_count = title_table_model.getRowCount();
            try {
                final var output = new OutputStreamWriter(new FileOutputStream(export_text.getText()), StandardCharsets.UTF_8);
                final var row = new AtomicInteger();
                while (row.get() < row_count) {
                    output.write(title_table_model.getValueAt(row.get(), 0).toString());
                    output.write("\n");
                    output.write(title_table_model.getValueAt(row.get(), 1).toString());
                    if (row.get() + 1 < row_count) output.write("\n");
                    row.getAndIncrement();
                }
                output.close();
            }
            catch (IOException error) {
                // TODO add error popup window
                error.printStackTrace();
            }
        });
    }

    private String getLinkTitle(BufferedReader buffered_link_input_stream) throws IOException {
        StringBuilder nextLine;
        var title_pattern = Pattern.compile("(?Uis).*?<title[^>]*>(.*?)(?:(</title>).*?)?");
        while ((nextLine = Optional.ofNullable(buffered_link_input_stream.readLine()).map(StringBuilder::new).orElse(null)) != null) {
            AtomicReference<Matcher> title_matcher = new AtomicReference<>(title_pattern.matcher(nextLine));
            if (title_matcher.get().matches()) {
                while (!nextLine.toString().contains("</title>")) {
                    var next = buffered_link_input_stream.readLine();
                    nextLine.append(next);
                }
                title_matcher.set(title_pattern.matcher(nextLine));
                if (title_matcher.get().matches()) return title_matcher.get().group(1);
            }
        }
        return"";
    }

    private void validateLink(AtomicReference<String> link, String url, String protocol, AtomicBoolean validated) {
        var relative_matcher = relative_pattern.matcher(link.get());
        var no_protocol_matcher = no_protocol_pattern.matcher(link.get());
        var nosource_slash_matcher = nosource_slash_pattern.matcher(link.get());
        var nosource_noslash_matcher = nosource_noslash_pattern.matcher(link.get());
        if (relative_matcher.matches() || nosource_noslash_matcher.matches()) {
            var index = url.lastIndexOf('/');
            if (url.charAt(index - 1) != '/') link.set(url.substring(0, index + 1) + link.get());
            else link.set(url + '/' + link.get());
            validated.set(true);
        }
        else if (no_protocol_matcher.matches()) {
            link.set(protocol + ':' + link.get());
            validated.set(true);
        }
        else if (nosource_slash_matcher.matches()) {
            var index = url.lastIndexOf('/');
            if (url.charAt(index - 1) != '/') link.set(url.substring(0, index) + link.get());
            else link.set(url + link.get());
            validated.set(true);
        }
    }

    private Charset getCharSet(String match) {
        switch (match) {
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
}
