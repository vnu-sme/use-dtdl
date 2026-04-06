package org.tzi.use.dtdl.gui.telemetry.visualizer;

import org.tzi.use.dtdl.actions.DTDLPluginState;
import org.tzi.use.dtdl.telemetry.TelemetryEngine;
import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.gui.views.View;
import org.tzi.use.main.Session;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@SuppressWarnings("serial")
public final class TelemetryMonitorView extends JPanel implements View, TelemetryUiListener {
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private final MainWindow mainWindow;
    private final Session session;
    private final TelemetryEngine engine;

    private final JLabel summaryLabel = new JLabel();
    private final JTextField filterField = new JTextField();
    private final JButton refreshButton = new JButton("Refresh");
    private final JButton clearButton = new JButton("Clear");

    private final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[]{"Time", "Status", "Adapter", "Object", "Telemetry", "HTTP", "Raw", "Normalized"},
            0
    ) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    private final JTable table = new JTable(tableModel);
    private final TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(tableModel);
    private final JTextArea detailsArea = new JTextArea();
    private final List<TelemetryUiRecord> records = new ArrayList<>();

    public TelemetryMonitorView(MainWindow mainWindow, Session session) {
        this.mainWindow = Objects.requireNonNull(mainWindow, "mainWindow required");
        this.session = Objects.requireNonNull(session, "session required");
        this.engine = DTDLPluginState.startTelemetryRuntime(session);

        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel top = new JPanel(new BorderLayout(8, 8));
        JPanel summaryRow = new JPanel(new BorderLayout(8, 8));
        summaryLabel.setText("Telemetry Monitor");
        summaryLabel.setFont(summaryLabel.getFont().deriveFont(Font.BOLD));
        summaryRow.add(summaryLabel, BorderLayout.WEST);

        JPanel filterRow = new JPanel(new BorderLayout(6, 6));
        filterField.setToolTipText("Filter telemetry rows");
        filterRow.add(new JLabel("Filter"), BorderLayout.WEST);
        filterRow.add(filterField, BorderLayout.CENTER);
        JPanel filterButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        filterButtons.add(refreshButton);
        filterButtons.add(clearButton);
        filterRow.add(filterButtons, BorderLayout.EAST);

        top.add(summaryRow, BorderLayout.NORTH);
        top.add(filterRow, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(24);
        table.setAutoCreateRowSorter(false);
        table.setFillsViewportHeight(true);
        table.setRowSorter(sorter);

        JScrollPane tableScroll = new JScrollPane(table);

        detailsArea.setEditable(false);
        detailsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        detailsArea.setLineWrap(true);
        detailsArea.setWrapStyleWord(true);

        JScrollPane detailsScroll = new JScrollPane(detailsArea);
        detailsScroll.setBorder(BorderFactory.createTitledBorder("Details"));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, detailsScroll);
        split.setResizeWeight(0.72);
        split.setDividerLocation(360);
        add(split, BorderLayout.CENTER);

        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int viewRow = table.rowAtPoint(e.getPoint());
                int viewCol = table.columnAtPoint(e.getPoint());
                if (viewRow < 0 || viewCol < 0) {
                    return;
                }

                int modelRow = table.convertRowIndexToModel(viewRow);
                int modelCol = table.convertColumnIndexToModel(viewCol);
                if (modelRow < 0 || modelRow >= records.size()) {
                    return;
                }

                TelemetryUiRecord r = records.get(modelRow);
                String columnName = table.getColumnName(viewCol);
                String value = getClickedCellValue(r, modelCol);

                detailsArea.setText(columnName + ": " + (value == null ? "" : value));
                detailsArea.setCaretPosition(0);
            }
        });

        refreshButton.addActionListener(e -> reloadHistory());
        clearButton.addActionListener(e -> {
            synchronized (records) {
                records.clear();
            }
            tableModel.setRowCount(0);
            detailsArea.setText("");
            updateSummary();
        });

        filterField.getDocument().addDocumentListener((SimpleDocumentListener) e -> applyFilter());

        reloadHistory();
        updateSummary();
    }

    private void reloadHistory() {
        tableModel.setRowCount(0);
        synchronized (records) {
            records.clear();
            if (engine != null) {
                List<TelemetryUiRecord> history = engine.history();
                for (int i = history.size() - 1; i >= 0; i--) {
                    addRecordInternal(history.get(i));
                }
            }
        }
        updateSummary();
    }

    private void addRecordInternal(TelemetryUiRecord record) {
        records.add(0, record);
        tableModel.insertRow(0, new Object[]{
                formatTime(record.timestamp),
                nvl(record.status),
                nvl(record.adapterName),
                nvl(record.objectName),
                nvl(record.telemetryName),
                nvl(record.httpStatus),
                shorten(record.rawValue, 48),
                shorten(record.normalizedValue, 48)
        });
    }

    private void updateSummary() {
        int adapterCount = DTDLPluginState.getRegisteredAdapters().size();
        int rowCount = tableModel.getRowCount();
        summaryLabel.setText("Telemetry Monitor   Adapters: " + adapterCount + "   Events: " + rowCount);
    }

    private void applyFilter() {
        String q = filterField.getText();
        if (q == null || q.isBlank()) {
            sorter.setRowFilter(null);
            return;
        }

        final String needle = q.trim().toLowerCase();
        sorter.setRowFilter(new RowFilter<>() {
            @Override
            public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                for (int i = 0; i < entry.getValueCount(); i++) {
                    Object v = entry.getValue(i);
                    if (v != null && String.valueOf(v).toLowerCase().contains(needle)) {
                        return true;
                    }
                }
                return false;
            }
        });
    }

    private String getClickedCellValue(TelemetryUiRecord r, int modelCol) {
        if (r == null) {
            return "";
        }

        return switch (modelCol) {
            case 0 -> formatTime(r.timestamp);
            case 1 -> nvl(r.status);
            case 2 -> nvl(r.adapterName);
            case 3 -> nvl(r.objectName);
            case 4 -> nvl(r.telemetryName);
            case 5 -> nvl(r.httpStatus);
            case 6 -> nvl(r.rawValue);
            case 7 -> nvl(r.normalizedValue);
            default -> "";
        };
    }

    private String formatTime(java.time.Instant t) {
        return t == null ? "" : TIME_FMT.format(t);
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }

    private String shorten(String s, int max) {
        if (s == null) {
            return "";
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max - 1) + "…";
    }

    @Override
    public void onTelemetryRecord(TelemetryUiRecord record) {
        SwingUtilities.invokeLater(() -> {
            synchronized (records) {
                addRecordInternal(record);
            }
            updateSummary();
        });
    }

    @Override
    public void detachModel() {
        if (engine != null) {
            engine.removeUiListener(this);
        }
    }

    private interface SimpleDocumentListener extends javax.swing.event.DocumentListener {
        void update(javax.swing.event.DocumentEvent e);

        @Override
        default void insertUpdate(javax.swing.event.DocumentEvent e) { update(e); }

        @Override
        default void removeUpdate(javax.swing.event.DocumentEvent e) { update(e); }

        @Override
        default void changedUpdate(javax.swing.event.DocumentEvent e) { update(e); }
    }
}