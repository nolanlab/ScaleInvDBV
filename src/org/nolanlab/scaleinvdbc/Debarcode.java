package org.nolanlab.scaleinvdbc;

import clustering.Datapoint;
import clustering.Dataset;
import dataIO.DatasetStub;
import dataIO.ImportConfigObject;
import flowcyt_fcs.ExportFCS;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableModel;
import net.sf.flowcyt.gp.module.csv2fcs.CSV2FCSApp;
import org.cytobank.fcs_files.events.FcsFile;

import util.Correlation;
import util.DefaultEntry;

import util.MatrixOp;
import util.logger;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author Nikolay
 */
public class Debarcode {

    private static final boolean filterDPBeforeExport = true;

    /**
     * @param args the command line arguments
     */
    public void run(Entry<String, String>[] FCS_FILE_LIST, String OUTPUT_PATH, boolean rescale, JTextArea txtOutput, JProgressBar prg) throws Exception {
        prg.setStringPainted(true);
        //1. Read config
        //2. Read FCS File List
        List<String> fcsFilePaths = null;

        s:
        for (Entry<String, String> s : FCS_FILE_LIST) {
            String fcsFilePath = s.getKey();
            String DEBARCODING_KEY = s.getValue();

            DatasetStub stub = DatasetStub.createFromFCS(new FcsFile(fcsFilePath));

            logger.print(stub.getFileName() + " has " + stub.getRowCount());
            DatasetStub bcds = DatasetStub.createFromTXT(new File(DEBARCODING_KEY));

            ImportConfigObject bcCO = new ImportConfigObject("tempBC", bcds.getShortColumnNames(), new String[0], ImportConfigObject.RescaleType.NONE, ImportConfigObject.TransformationType.NONE, 1, 0.05, false, -1, -1, -1);
            Dataset bcd = dataIO.DatasetImporter.importDataset(new DatasetStub[]{bcds}, bcCO);
            Datapoint[] bcdp = bcd.getDatapoints();

            ArrayList<String> bcCol = new ArrayList<>();
            ArrayList<Integer> bcColIdx = new ArrayList<>();
            for (int i = 0; i < stub.getShortColumnNames().length; i++) {
                String c = stub.getShortColumnNames()[i];
                if (c.toLowerCase().matches(".*pd[0-9][0-9][0-9].*")) {
                    bcCol.add(c);
                    bcColIdx.add(i);
                }
            }

            if (bcCol.size() != 6) {
                throw new IllegalStateException("Failed to identidy 6 BC channels in this file.  Matching expression: c.toLowerCase().matches(\".*pd[0-9][0-9][0-9].*\"); \n Columns identidied:" + bcCol.toString());
            }

            String[] bcColNames = bcCol.toArray(new String[bcCol.size()]);

            DefaultTableModel dtm = new DefaultTableModel(new String[]{"BC channel name", "Debarcoding File Column"}, 0);

            for (int i = 0; i < bcColNames.length; i++) {
                dtm.addRow(new String[]{bcColNames[i], bcds.getShortColumnNames()[i]});
            }

            JScrollPane js = new JScrollPane();

            js.setViewportView(new JTable(dtm));

            if (!frmMain.batchMode) {
                if (JOptionPane.showConfirmDialog(null, js, "Confirm that the barcoding channels match the debarcoding file column", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
                    continue s;
                }
            }

            ImportConfigObject config = new ImportConfigObject("temp", stub.getShortColumnNames(), new String[0], ImportConfigObject.RescaleType.NONE, ImportConfigObject.TransformationType.ASINH, 5, 0.05, false, -1, -1, -1);

            txtOutput.setText(txtOutput.getText() + "\n" + "Opening file " + fcsFilePath + "...");

            FilteredDatapoint[] d = new FilteredDatapoint[(int) stub.getRowCount()];
            String[] colNames = stub.getShortColumnNames();
            txtOutput.setText(txtOutput.getText() + "\n" + "Importing cell events from FCS file...");
            prg.setIndeterminate(false);
            for (int i = 0; i < stub.getRowCount(); i++) {

                if (i % 1000 == 0) {
                    prg.setValue((100 * i) / (int) stub.getRowCount());
                }

                double[] rawVec = stub.getRow(i);
                double[] vec = new double[bcColIdx.size()];
                for (int j = 0; j < bcColIdx.size(); j++) {
                    vec[j] = rawVec[bcColIdx.get(j)];
                    vec[j] /= 5.0;
                    vec[j] = Math.log(vec[j] + Math.sqrt(vec[j] * vec[j] + 1));
                }
                d[i] = new FilteredDatapoint(new Datapoint(null, vec, rawVec, i));
            }

            prg.setIndeterminate(false);

            if (rescale) {
                for (int i = 0; i < bcColNames.length; i++) {
                    txtOutput.setText(txtOutput.getText() + "\n" + "Rescaling barcoding channel " + bcColNames[i]);
                    prg.setValue((100 * i) / bcColIdx.size());
                    double[] vec = new double[d.length];
                    for (int j = 0; j < d.length; j++) {
                        vec[j] = d[j].getBCVector()[i];
                    }
                    Arrays.sort(vec);
                    double pct = vec[(int) (vec.length * 0.15)];
                    for (int j = 0; j < d.length; j++) {
                        d[j].getBCVector()[i] -= pct;
                    }
                }

                for (int i = 0; i < bcColNames.length; i++) {
                    double[] vec = new double[d.length];
                    for (int j = 0; j < d.length; j++) {
                        vec[j] = d[j].getBCVector()[i];
                    }
                    Arrays.sort(vec);
                    double pct = vec[(int) (vec.length * 0.85)];
                    for (int j = 0; j < d.length; j++) {
                        d[j].getBCVector()[i] /= pct;
                    }
                }
            }

            HashMap<String, List<FilteredDatapoint>> groups = new HashMap<>();
            for (int i = 0; i < bcdp.length; i++) {
                double[] vec = bcdp[i].getVector();
                for (int j = 0; j < vec.length; j++) {
                    vec[j] -= 0.5;
                    vec[j] *= 2;
                }
                groups.put(bcdp[i].getFullName(), new ArrayList<>());
            }

            txtOutput.setText(txtOutput.getText() + "\n" + "Debarcoding...");
            prg.setStringPainted(true);

            for (final FilteredDatapoint fdp : d) {
                final double[] vec = MatrixOp.copy(fdp.getBCVector());
                double sum = 0;
                for (double dbl : vec) {
                    sum += dbl;
                }
                fdp.setSumSQ(sum);
                if (fdp.getID() % (d.length / 100) == 0) {
                    prg.setValue(fdp.getID() / (d.length / 100));
                }
                Arrays.sort(bcdp, new Comparator<Datapoint>() {
                    @Override
                    public int compare(Datapoint o1, Datapoint o2) {
                        return (int) Math.signum(Debarcode.getT(fdp.getBCVector(), o2.getVector()) - Debarcode.getT(fdp.getBCVector(), o1.getVector()));
                    }
                });

                /*
                 logger.print(Arrays.toString(fdp.getBCVector()));
                 for (int i = 0; i < bcdp.length; i++) {
                 logger.print(Arrays.toString(bcdp[i].getVector()));
                 logger.print("T:" + Debarcode.getT(fdp.getBCVector(), bcdp[i].getVector())+" C:"+Debarcode.getCorrel(fdp.getBCVector(), bcdp[i].getVector()));
                 }
                 */
                fdp.setZscoreSep(Debarcode.getT(fdp.getBCVector(), bcdp[0].getVector()));
                fdp.setPosToNegRatio(getAvgRatio(fdp.getBCVector(), bcdp[0].getVector()));
                groups.get(bcdp[0].getFullName()).add(fdp);
            }

            logger.print("Exporting FCS files");
            txtOutput.setText(txtOutput.getText() + "\nDone\n" + "Exporting FCS files...");
            int sz = groups.entrySet().size();
            int k = 0;
            final Entry<String, List<FilteredDatapoint>>[] groupArr = groups.entrySet().toArray(new Entry[groups.size()]);

            ExecutorService es = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

            for (int gr = 0; gr < groupArr.length; gr++) {
                Entry<String, List<FilteredDatapoint>> e = groupArr[gr];
                txtOutput.setText(txtOutput.getText() + "\n" + e.getKey() + ": " + e.getValue().size());

                prg.setValue((k * 100) / sz);
                k++;
                if (e.getValue().size() > 0) {
                    List<String> lstShortParam = new ArrayList<>();
                    List<String> lstLongParam = new ArrayList<>();

                    appendCorrelToCenter(e.getValue());

                    FilteredDatapoint[] res = null;
                    if (filterDPBeforeExport) {
                        txtOutput.setText(txtOutput.getText() + "\nFiltering...");
                        res = filterDPList(e.getValue());
                        txtOutput.setText(txtOutput.getText() + " " + res.length + " cells left after filtering");
                    } else {
                        res = e.getValue().toArray(new FilteredDatapoint[e.getValue().size()]);
                    }

                    int t = 0;
                    stub = DatasetStub.createFromFCS(new FcsFile(fcsFilePath));
                    for (; t < stub.getShortColumnNames().length; t++) {
                        lstShortParam.add(stub.getShortColumnNames()[t]);
                        lstLongParam.add(stub.getLongColumnNames()[t]);

                    }
                    for (; t < stub.getShortColumnNames().length + FilteredDatapoint.filterParamNames.length; t++) {
                        lstShortParam.add("DBC" + ((t - stub.getShortColumnNames().length) + 1));
                        lstLongParam.add(FilteredDatapoint.filterParamNames[t - stub.getShortColumnNames().length]);
                    }

                    float[][] evt = new float[res.length][];
                    for (int i = 0; i < evt.length; i++) {
                        if (i % 1000 == 0) {
                            System.err.println("Concatenating: " + i);
                        }
                        FilteredDatapoint dp = res[i];
                        double[] vec = dp.getSideVector();
                        double[] filt = dp.getFilterVec();
                        double[] concat = MatrixOp.concat(vec, filt);
                        float[] f = new float[concat.length];
                        for (int j = 0; j < f.length; j++) {
                            f[j] = (float) concat[j];
                        }
                    }

                    AtomicInteger cntDone = new AtomicInteger(0);
                    es.execute(new Runnable() {
                        public void run() {
                            try {
                                new ExportFCS().writeFCSAsFloat(OUTPUT_PATH + File.separator + (new File(fcsFilePath).getName().split("\\.")[0]) + "_" + e.getKey(), evt, lstShortParam.toArray(new String[lstShortParam.size()]), lstLongParam.toArray(new String[lstLongParam.size()]));
                                //writeFCSViaCVS(evt, lst, OUTPUT_PATH + File.separator + (new File(fcsFilePath).getName().split("\\.")[0]) + "_" + e.getKey());
                                prg.setValue((cntDone.addAndGet(1) * 100) / groupArr.length);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    });

                }
            }
            es.shutdown();
            boolean finshed = es.awaitTermination(100000, TimeUnit.HOURS);

            prg.setValue(100);
        }
        txtOutput.setText(txtOutput.getText() + "\n" + "Debarcoding done");
        prg.setVisible(false);
    }

    private FilteredDatapoint[] filterDPList(List<FilteredDatapoint> lst) {
        double sumBCths = 1.0;
        double avgRatioThs = 2.5;
        double ZscoreSepList = 2.5;
        double corrRemovalPct = 0.25;
        ArrayList<FilteredDatapoint> outList = new ArrayList<>();
        for (FilteredDatapoint fdp : lst) {
            if (fdp.getZscoreSep() > ZscoreSepList && fdp.getSum() > sumBCths && fdp.getPosToNegRatio() > avgRatioThs) {
                outList.add(fdp);
            }
        }
        FilteredDatapoint[] out = outList.toArray(new FilteredDatapoint[outList.size()]);
        Arrays.sort(out, new Comparator<FilteredDatapoint>() {
            @Override
            public int compare(FilteredDatapoint o1, FilteredDatapoint o2) {
                return (int) Math.signum(o1.getCorrelToCentroid() - o2.getCorrelToCentroid());
            }
        });
        return Arrays.copyOfRange(out, (int) (out.length * corrRemovalPct), out.length);
    }

    private void writeFCSViaCVS(double[][] evt, List<Entry<String, String>> lst, String path) throws IOException {
        File csv = new File(path + ".csv");
        int dim = lst.size();
        BufferedWriter bw = new BufferedWriter(new FileWriter(csv));
        System.err.println(csv);
        for (int i = 0; i < dim; i++) {
            bw.write(lst.get(i).getKey() + ":" + lst.get(i).getValue());
            if (i < dim - 1) {
                bw.write(",");
            }
        }
        bw.write("\n");

        for (int i = 0; i < evt.length; i++) {
            for (int j = 0; j < dim; j++) {
                bw.write(String.valueOf(evt[i][j]));
                if (j < dim - 1) {
                    bw.write(",");
                }
            }
            bw.write("\n");
        }
        bw.flush();
        bw.close();
        CSV2FCSApp.main(new String[]{"-InputFile:" + csv.getPath()});
        csv.delete();
    }

    private static double getT(double[] vec, double[] bcvec) {
        double avg1 = 0, avg2 = 0;
        int cnt1 = 0, cnt2 = 0;
        for (int i = 0; i < vec.length; i++) {
            if (bcvec[i] > 0) {
                avg1 += vec[i];
                cnt1++;
            }
            if (bcvec[i] < 0) {
                avg2 += vec[i];
                cnt2++;
            }
        }
        avg1 /= cnt1;
        avg2 /= cnt2;

        double sd = 0;
        for (int i = 0; i < vec.length; i++) {
            if (bcvec[i] > 0) {
                sd += Math.pow(vec[i] - avg1, 2);
            }
            if (bcvec[i] < 0) {
                sd += Math.pow(vec[i] - avg2, 2);
            }
        }
        sd /= (cnt1 + cnt2) - 1;
        sd = Math.sqrt(sd);
        return (avg1 - avg2) / sd;
    }

    private static double getCorrel(double[] vec, double[] bcvec) {
        return Correlation.getCenteredCorrelation(vec, bcvec);
    }

    private static double getAvgRatio(double[] vec, double[] bcvec) {
        double avg1 = 0, avg2 = 0;
        int cnt1 = 0, cnt2 = 0;
        for (int i = 0; i < vec.length; i++) {
            if (bcvec[i] > 0) {
                avg1 += vec[i] * vec[i];
                cnt1++;
            }
            if (bcvec[i] < 0) {
                avg2 += vec[i] * vec[i];
                cnt2++;
            }
        }
        if (avg2 == 0 && avg1 > 0) {
            return 1000;
        }
        return Math.sqrt(avg1) / Math.sqrt(avg2);
    }

    private static void appendCorrelToCenter(List<FilteredDatapoint> in) {

        FilteredDatapoint.exposeBCVec = true;
        double[] vec = new double[in.get(0).getVector().length];
        for (Datapoint d : in) {
            vec = MatrixOp.sum(vec, d.getVector());
        }
        MatrixOp.mult(vec, 1.0 / in.size());
        for (FilteredDatapoint d : in) {
            double cor = Correlation.getCenteredCorrelation(vec, d.getVector()) * 0.9999;
            cor = 0.5 * Math.log((1 + cor) / (1 - cor));
            d.setCorrelToCentroid(cor);
        }

    }

}
