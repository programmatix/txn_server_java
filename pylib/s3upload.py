#!/usr/bin/env python

# This module contains the s3_upload function which uploads stuff into our
# S3 bucket. The access and secret keys are my own, but I've deemed them rather
# benign (not tied to any credit card or anything). While nasty, I can't think
# of a better replacement currently.

import os
import os.path
import tinys3
import argparse
import gzip
import sys
from datetime import datetime

S3_BUCKET = "sdk-testresults.couchbase.com"
S3_SECRET = "PRnYRzepuMBHNTJ5MRRsfL6kxCoJ/VYSb5XQMvZ9"
S3_ACCESS = "AKIAJIAHNTVSCR4PWVAQ"
S3_DIR = "sdkd"


def _upload_data(file):
    conn = tinys3.Connection(S3_ACCESS, S3_SECRET, tls=True)
    f = open(file, 'rb')
    conn.upload(file, f, S3_BUCKET)
    return "http://{0}.s3.amazonaws.com/{1}".format(S3_BUCKET, file)


def s3_upload(fname, dstname = None):
    data = open(fname, "r").read()
    if not dstname:
        dstname = os.path.basename(fname)

    return _upload_data(data, dstname)

if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("-f", "--file", help = "Filename to upload",
                    required = True)


    opts = ap.parse_args()
    today = datetime.utcnow().timetuple()
    compressed_log = ""

    for i in today:
        compressed_log += "{0}".format(i)

    log_file = opts.file

    with open(log_file, 'rb') as f_in, gzip.open('{0}.gz'.format(compressed_log), 'wb') as f_out:
        f_out.writelines(f_in)
        f_out.close()
        f_in.close()

    url = _upload_data('{0}.gz'.format(compressed_log))
    print "{0}".format(url)
    os.remove("{0}.gz".format(compressed_log))
    os.remove(log_file)