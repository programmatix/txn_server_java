#!/usr/bin/env python
import sys
import os
import os.path

from cbsdk.testconfig import ConfigOptionCollection, ConfigOption
from cbsdk.driver import DriverInet

SRC_ROOT = os.path.join(os.path.dirname(__file__), '..')
SRC_ROOT = os.path.abspath(SRC_ROOT)

BUILD_ROOT = os.path.join(SRC_ROOT, 'pkgcache')

class ReleaseJar(object):
    def get_jarfile(self):
        return os.path.join(BUILD_ROOT,
                            'target',
                            'sdkd-java-{0}'.format(self.vstring)) + ".jar"

    def build(self, force = False):
        if os.path.exists(self.get_jarfile()) and not force:
            return self

        txt = open(os.path.join(BUILD_ROOT, 'pom.template.xml'), 'r').read()
        txt = txt.replace("__SDKD_VERSION__", self.vstring)


        txt = txt.replace("__COUCHBASE_CLIENT_VERSION__", self.version)

        fname = os.path.join(BUILD_ROOT, 'pom-{0}.xml'.format(self.version))
        fp = open(fname, 'w')
        fp.write(txt)
        fp.close()
        os.system("mvn -f {0} package".format(fname))

        return self

    def __init__(self, version):
        self.vstring = 'CB-{0}'.format(version)
        self.version = version


class DriverImplementation(DriverInet):
    caps = {
        'ds_shared' : False,
        'preamble' : False,
        'cancel' : True,
        'continuous' : True,
    }

    _jdk_log_levels = ("SEVERE", "WARNING", "INFO", "FINE", "FINER", "FINEST")

    jcb_confcoll = ConfigOptionCollection('Java SDKD options', '', [
        ConfigOption("jcb_shared", 100, int,
                     "Share handle among this many threads"),

        ConfigOption("jcb_version", '1.0.3', str, "Java client to use"),

        ConfigOption("jcb_qtmo", 0, int, "Wait timeout for scheduling (ms)"),

        ConfigOption("jcb_qcap", 0, int, "Operation Queue Capacity (ms)"),

        ConfigOption("jcb_force_build", False, bool, "Force rebuilding"),

        ConfigOption("jcb_log_prefs", None, str, "Path to logging preferences "
                     "file"),

        #ConfigOption("jcb_cli_loglvl", None, str, "Level for CouchbaseClient "
        #             "logging",
        #             choices = _jdk_log_levels),
        #
        #ConfigOption("jcb_sdkd_loglvl", None, str, "Level for SDKD logging",
        #             choices = _jdk_log_levels),

        ConfigOption('jcb_jvm_option', [], list, "JVM -D options",
                     action = 'append')

    ])

    _INSTANCE = None
    _CLIEXTRA = None

    @classmethod
    def bootstrap(cls):
        coll = cls.jcb_confcoll
        cls._INSTANCE = ReleaseJar(coll['jcb_version'].get())
        cls._INSTANCE.build(force = coll['jcb_force_build'].get())

    @classmethod
    def set_debugger(cls, debugger):
        pass

    @classmethod
    def set_extra_args(cls, extra):
        cls._CLIEXTRA = extra

    @classmethod
    def get_config_options(cls):
        return [ cls.jcb_confcoll ]

    def __init__(self, execargs, **options):
        coll = self.jcb_confcoll
        sdkd_exe = self._INSTANCE.get_jarfile()


        execargs = [ "java" ]
        for prop in coll['jcb_jvm_option'].get():
            execargs.append("-D" + prop)

        logprefs = coll['jcb_log_prefs'].get()

        if logprefs:
            logprefs = os.path.abspath(logprefs)
            execargs += [ "-Djava.util.logging.config.file=" + logprefs ]

        execargs += [ "-jar", sdkd_exe ]
        execargs += [ "-shared", str(coll['jcb_shared'].get()) ]
        execargs += [ "-portfile", "portfile.txt" ]
        execargs += [ "-qtmo", str(coll['jcb_qtmo'].get()) ]
        execargs += [ "-qcap", str(coll['jcb_qcap'].get()) ]

        if self._CLIEXTRA:
            execargs += self._CLIEXTRA.split(" ")

        options['portinfo'] = 'portfile.txt'
        print execargs
        super(DriverImplementation, self).__init__(execargs, **options)

if __name__ == "__main__":
    jf = ReleaseJar('1.0.3').build().get_jarfile()
    os.system("java -jar {0} -h".format(jf))

    js =  ReleaseJar('1.1-dp3').build().get_jarfile()
    os.system("java -jar {0} -h".format(jf))
