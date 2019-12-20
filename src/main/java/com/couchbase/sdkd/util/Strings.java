/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.couchbase.sdkd.util;


/**
 *
 * @author mnunberg
 */
public class Strings {

    public final static String REQID = "ReqID";
    public final static String CMD = "Command";
    public final static String HID = "Handle";
    public final static String REQDATA = "CommandData";
    public final static String RESDATA = "ResponseData";
    public final static String DSREQ_DSTYPE = "DSType";
    public final static String DSREQ_DS = "DS";
    public final static String STATUS = "Status";
    public final static String ERRSTR = "ErrorString";
    public final static String DSSEED_KSIZE = "KSize";
    public final static String DSSEED_VSIZE = "VSize";
    public final static String DSSEED_COUNT = "Count";
    public final static String DSSEED_KSEED = "KSeed";
    public final static String DSSEED_VSEED = "VSeed";
    public final static String DSSEED_REPEAT = "Repeat";
    public final static String DSNQ_COUNT = "NQCount";

    //result strings
    public final static String SUCCESS = "SUCCESS";
    public final static String ENOENT = "ENOENT";
    public final static String FAILURE = "FAIL";

    public final static String DSSPATIAL_KSEED = "KSeed";
    public final static String DSSPATIAL_VSEED = "VSeed";
    public final static String DSSPATIAL_COUNT = "Count";
    public final static String DSSPATIAL_LATBASE = "LatBase";
    public final static String DSSPATIAL_LONGBASE = "LongBase";
    public final static String DSSPATIAL_LATINCR = "LatIncr";
    public final static String DSSPATIAL_LONGINCR = "LongIncr";
    public final static String DSINLINE_ITEMS = "Items";
    public final static String DS_ID = "ID";
    public final static String DSREQ_OPTS = "Options";
    public final static String DSREQ_DELAY = "DelayMsec";
    public final static String DSREQ_DELAY_MIN = "DelayMin";
    public final static String DSREQ_DELAY_MAX = "DelayMax";
    public final static String DSREQ_FULL = "Detailed";
    public final static String DSREQ_MULTI = "Multi";
    public final static String DSREQ_EXPIRY = "Expiry";
    public final static String DSREQ_ITERWAIT = "IterWait";
    public final static String DSREQ_CONTINUOUS = "Continuous";
    public final static String DSREQ_TIMERES = "TimeRes";
    public final static String DSRES_STATS = "Summary";
    public final static String DSRES_FULL = "Details";
    public final static String DSRES_TIMINGS = "Timings";
    public final static String DSREQ_ASYNC = "Async";
    public final static String DS_PRELOAD = "Preload";

    // Durability Requirements
    public final static String DSREQ_DUR_PERSIST = "PersistTo";
    public final static String DSREQ_DUR_REPLICATE = "ReplicateTo";
    public final static String DSREQ_REPLICA_READ = "ReplicaRead";

    public final static String TMS_BASE = "Base";
    public final static String TMS_COUNT = "Count";
    public final static String TMS_MIN = "Min";
    public final static String TMS_MAX = "Max";
    public final static String TMS_AVG = "Avg";
    public final static String TMS_PERCENTILE = "Percentile";
    public final static String TMS_ECS = "Errors";
    public final static String TMS_WINS = "Windows";
    public final static String TMS_STEP = "Step";
    public final static String EXTRA = "Extra";
    public final static String FUNC_TEST_ERR_PREFIX = "_FUNC:";
    public final static String FUNC_TEST_CMD_PREFIX = "FN_:";
    public final static String HANDLE_HOSTNAME = "Hostname";
    public final static String HANDLE_PORT = "Port";
    public final static String HANDLE_BUCKET = "Bucket";
    public final static String HANDLE_USERNAME = "Username";
    public final static String HANDLE_PASSWORD = "Password";
    public final static String HANDLE_OPTIONS = "Options";
    public final static String HANDLE_OPT_TMO = "Timeout";
    public final static String HANDLE_OPT_BACKUPS = "OtherNodes";
    public final static String HANDLE_SSL = "SSL";
    public final static String HANDLE_CLIENTCERT = "ClientCert";
    public final static String HANDLE_AUTOFAILOVER = "AutoFailover";
    public final static String LOGIN_ADMIN = "Administrator";

    // TTL Command parameters
    public final static String TTL_SECONDS = "Seconds";

    // View Query Parameters
    public final static String QVOPT_STALE = "stale";
    public final static String QVOPT_LIMIT = "limit";
    public final static String QVOPT_ONERR = "on_error";
    public final static String QVOPT_DESC = "descending";
    public final static String QVOPT_SKIP = "skip";
    public final static String QVOPT_REDUCE = "reduce";
    public final static String QVOPT_INCDOCS = "include_docs";
    public final static String QV_ONERR_CONTINUE = "continue";
    public final static String QV_ONERR_STOP = "stop";
    public final static String QV_STALE_UPDATEAFTER = "update_after";

    // View Load Options
    public final static String V_SCHEMA = "Schema";
    public final static String V_INFLATEBASE = "InflateContent";
    public final static String V_INFLATECOUNT = "InflateLevel";
    public final static String V_KIDENT = "KIdent";
    public final static String V_KSEQ = "KVSequence";
    public final static String V_DESNAME = "DesignName";
    public final static String V_MRNAME = "ViewName";

    // View Query Control Options
    public final static String V_QOPTS = "ViewParameters";
    public final static String V_QDELAY = "ViewQueryDelay";
    public final static String V_QITERCOUNT = "ViewQueryCount";


    //Spatial View Query options
    public final static String QVOPT_START_RANGE = "start_range";
    public final static String QVOPT_END_RANGE = "end_range";

    // Not strings, but they are protocol constants
    public final static int VR_IX_IDENT = 0;


    // N1QL Options
    public final static String NQ_PARAM = "NQParam";
    public final static String NQ_INDEX_ENGINE = "NQIndexEngine";
    public final static String NQ_INDEX_TYPE = "NQIndexType";
    public final static String NQ_PREPARED = "NQPrepared";
    public final static String NQ_PARAMETERIZED = "NQParameterized";
    public final static String NQ_DEFAULT_INDEX_NAME = "NQDefaultIndexName";
    public final static String NQ_PARAMVALUES ="NQParamValues";
    public final static String NQ_SCANCONSISTENCY = "NQScanConsistency";
    public final static String NQ_DML = "NQDml";

    //SD Options
    public final static String SD_SCHEMA = "SDSchema";
    public final static String SD_PATH = "SDPath";
    public final static String SD_VALUE = "SDValue";
    public final static String SD_COMMAND = "SDCommand";
    public final static String SD_COUNT = "Count";

    /** FTS field options */
    public final static String FTS_INDEX_NAME = "FTSIndexName";
    public final static String FTS_CONSISTENCY="FTSConsistency";

    //internal constants
    public final static int DCP_COUNT = 100;
    //Txn Options:
    public final static String TXN_PAYLOAD = "TXNKey";
    public final static String TXN_NODES_INIT = "TXN_NODES_INIT";
    public final static String TXN_REPLICAS = "TXN_REPLICAS";
    public final static String TXN_COMMIT = "TXN_COMMIT";
    public final static String TXN_OP_TYPE = "TXN_OP_TYPE";
    public final static String TXN_GROUP = "TXN_GROUP";
    public final static String TXN_OS_TYPE = "TXN_OS_TYPE";
    public final static String TXN_TOTAL_DOCS = "totaldocs";
    public final static String TXN_BATCHSIZE = "batchsize";
    public final static String TXN_NTHREADS = "nthreads";
    public final static String TXN_ERRORS = "txn_errors";
    public final static String TXN_KEYS = "txnKeys";
    public final static String TXN_LOAD = "TXN_LOAD";
    public final static String TXN_UPDATE = "TXN_UPDATE";
    public final static String TXN_UPDATEKEYS= "UpdateKeys";
    public final static String TXN_DELETEKEYS = "DeleteKeys";
    public final static String TXN_LOAD_PROCESS = "Trying to Load documents into the DB using Transactions";
    //Create Txn Params
    public final static String TXN_DURABILITY = "TXN_DURABILITY";
    public final static String TXN_TIMEOUT = "TXN_TIMEOUT";
    public final static String TXN = "TXN";
    public final static String DEFAULT_KEY = "Test";
    public final static String CONTENT_NAME= "content";
    public final static String DEFAULT_CONTENT_VALUE= "default";
    public final static String UPDATED_CONTENT_VALUE= "updated";


}
