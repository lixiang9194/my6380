package simpledb;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TableStats represents statistics (e.g., histograms) about base tables in a
 * query. 
 * 
 * This class is not needed in implementing lab1 and lab2.
 */
public class TableStats {

    private static final ConcurrentHashMap<String, TableStats> statsMap = new ConcurrentHashMap<String, TableStats>();

    static final int IOCOSTPERPAGE = 1000;

    public static TableStats getTableStats(String tablename) {
        return statsMap.get(tablename);
    }

    public static void setTableStats(String tablename, TableStats stats) {
        statsMap.put(tablename, stats);
    }
    
    public static void setStatsMap(HashMap<String,TableStats> s)
    {
        try {
            java.lang.reflect.Field statsMapF = TableStats.class.getDeclaredField("statsMap");
            statsMapF.setAccessible(true);
            statsMapF.set(null, s);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

    }

    public static Map<String, TableStats> getStatsMap() {
        return statsMap;
    }

    public static void computeStatistics() {
        Iterator<Integer> tableIt = Database.getCatalog().tableIdIterator();

        System.out.println("Computing table stats.");
        while (tableIt.hasNext()) {
            int tableid = tableIt.next();
            TableStats s = new TableStats(tableid, IOCOSTPERPAGE);
            setTableStats(Database.getCatalog().getTableName(tableid), s);
        }
        System.out.println("Done.");
    }

    /**
     * Number of bins for the histogram. Feel free to increase this value over
     * 100, though our tests assume that you have at least 100 bins in your
     * histograms.
     */
    static final int NUM_HIST_BINS = 100;
    private int totalTuples;
    private int totalPages;
    private FieldHistogram[] his;
    private int ioCostPerPage;

    /**
     * Create a new TableStats object, that keeps track of statistics on each
     * column of a table
     * 
     * @param tableid
     *            The table over which to compute statistics
     * @param ioCostPerPage
     *            The cost per page of IO. This doesn't differentiate between
     *            sequential-scan IO and disk seeks.
     */
    public TableStats(int tableid, int ioCostPerPage) {
        this.ioCostPerPage = ioCostPerPage;
        HeapFile file = (HeapFile)Database.getCatalog().getDatabaseFile(tableid);
        totalPages =  file.numPages();

        TupleDesc td = Database.getCatalog().getTupleDesc(tableid);
        int nFields = td.numFields();
        his = new FieldHistogram[nFields];

        int[] max = new int[nFields];
        int[] min = new int[nFields];

        //计算每列最大值、最小值
        TransactionId tid = new TransactionId();
        DbFileIterator itp = file.iterator(tid);
        try {
            itp.open();
            while (itp.hasNext()) {
                Tuple t = itp.next();
                for (int i = 0; i < nFields; i++) {
                    Field f = t.getField(i);
                    if (f.getType().equals(Type.INT_TYPE)) {
                        int v = ((IntField) f).getValue();
                        if (max[i] < v) {
                            max[i] = v;
                        }
                        if (min[i] > v) {
                            min[i] = v;
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        //初始化直方图
        for (int i = 0; i < nFields; i++) {
            if (td.getFieldType(i).equals(Type.INT_TYPE)) {
                his[i] = new IntHistogram(NUM_HIST_BINS, min[i], max[i]);
            } else {
                his[i] = new StringHistogram(NUM_HIST_BINS);
            }
        }

        //构造直方图
        try {
            itp.rewind();
            while (itp.hasNext()) {
                totalTuples++;
                Tuple t = itp.next();
                for (int i = 0; i < nFields; i++) {
                    his[i].addValue(t.getField(i));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Estimates the cost of sequentially scanning the file, given that the cost
     * to read a page is costPerPageIO. You can assume that there are no seeks
     * and that no pages are in the buffer pool.
     * 
     * Also, assume that your hard drive can only read entire pages at once, so
     * if the last page of the table only has one tuple on it, it's just as
     * expensive to read as a full page. (Most real hard drives can't
     * efficiently address regions smaller than a page at a time.)
     * 
     * @return The estimated cost of scanning the table.
     */
    public double estimateScanCost() {
         return totalPages * ioCostPerPage;
    }

    /**
     * This method returns the number of tuples in the relation, given that a
     * predicate with selectivity selectivityFactor is applied.
     * 
     * @param selectivityFactor
     *            The selectivity of any predicates over the table
     * @return The estimated cardinality of the scan with the specified
     *         selectivityFactor
     */
    public int estimateTableCardinality(double selectivityFactor) {
        return (int)(totalTuples * selectivityFactor);
    }

    /**
     * The average selectivity of the field under op.
     * @param field
     *        the index of the field
     * @param op
     *        the operator in the predicate
     * The semantic of the method is that, given the table, and then given a
     * tuple, of which we do not know the value of the field, return the
     * expected selectivity. You may estimate this value from the histograms.
     * */
    public double avgSelectivity(int field, Predicate.Op op) {
        // some code goes here
        return 1.0;
    }

    /**
     * Estimate the selectivity of predicate <tt>field op constant</tt> on the
     * table.
     * 
     * @param field
     *            The field over which the predicate ranges
     * @param op
     *            The logical operation in the predicate
     * @param constant
     *            The value against which the field is compared
     * @return The estimated selectivity (fraction of tuples that satisfy) the
     *         predicate
     */
    public double estimateSelectivity(int field, Predicate.Op op, Field constant) {
        return his[field].estimateSelectivity(op, constant);
    }

    /**
     * return the total number of tuples in this table
     * */
    public int totalTuples() {
        return totalTuples;
    }

}
