<template>
  <div>
    <div class="runallgroup">
      <span v-show="tcLoading" class="tcLoading">Loading</span>
      <span v-show="running" class="running">Running</span>
<!--        <span v-if="!$store.state.testRunner.isClientTest">-->
        <span v-if="running" class="timerFont">{{elapsedTestTime}}s</span>
<!--        </span>-->
      <div class="divider"></div>
      <div class="divider"></div>

      <span v-if="$store.state.testRunner.isUserSuppliedTestFixture">
            <textarea id="userSuppliedTestFixtureText" v-model="userSuppliedTestFixtureText" cols="80" rows="15" placeholder="Paste Resource JSON or XML here, and select the proper format option."></textarea>
      </span>

      <span v-if="!$store.state.testRunner.isClientTest">
            <button :disabled="running" class="clearLogsButton" @click="doClearLogs()" title="Temporarily clear TestReports for this browser tab">&#x1f5d1; Clear Logs</button>
<!--            <template v-if="theChannelObj.channelType === 'mhd'">-->
                <input type="checkbox" id="doTls" v-model="tlsOption">
                <label for="doTls" title="Use TLS Proxy? References in response will be re-based using TLS proxy hostname and port.">Use TLS?</label>
                <div class="divider"></div>
<!--            </template>-->
            <input type="checkbox" id="doGzip" v-model="gzip">
            <label for="doGzip">GZip?</label>
            <div class="divider"></div>
            <button v-bind:class="{'button-selected': json, 'button-not-selected': !json}" @click="doJson()">JSON</button>
            <button v-bind:class="{'button-selected': !json, 'button-not-selected': json}" @click="doXml()">XML</button>
      </span>

      <div class="divider"></div>
      <div class="divider"></div>
      <button :disabled="running" class="runallbutton" @click.stop="doRunAll()">Run All</button>
    </div>

      <h3 class="conformance-tests-header" :title="`There are ${scriptNames.length} tests in this test collection. Pass:${passingTestCount}, fail:${failingTestCount}, notRun:${notRunTestCount}. ${elapsedTestTime > 0 ? 'Previous test(s) took ' + elapsedTestTime + ' seconds to run.':''}`">Tests<test-progress-bar class="testProgressBar" :fail-count="failingTestCount" :pass-count="passingTestCount" :not-run-count="notRunTestCount" :total-count="scriptNames.length"></test-progress-bar></h3>
      <div>
      <div class="testBarMargin" v-for="(name, i) in scriptNames"
           :key="name + i" >
        <div v-bind:class="{
                                'pass': status[name] === 'pass' && colorful,
                                'pass-plain-header': status[name] === 'pass' && !colorful,
                                'fail': status[name] === 'fail' && colorful,
                                'fail-plain-header': status[name] === 'fail' && !colorful,
                                'error': status[name] === 'error',
                                'not-run':  status[name] === 'not-run' && colorful /*  !status[name] */,
                                'not-run-plain': status[name] === 'not-run' && ! colorful,
                           }" @click.prevent="openTest(name)">

          <script-status v-if="!statusRight" :status-right="statusRight" :name="name"> </script-status>

          <template v-if="! isClient">
            <template v-if="isDebugFeatureEnabled">
              <template v-if="isPreviousDebuggerStillAttached(i)">
                <span class="breakpointColumnHeader" title="A debugger is running for this TestScript.">&#x1F41E;</span> <!-- lady beetle icon -->
              </template>
              <template v-else-if="$store.state.testRunner.currentTest === name && ! isDebuggable(i) && ! isResumable(i)">
                <span class="breakpointColumnHeader infoIcon" title="Add at least one breakpoint in the column below to enable debugging.">&nbsp;&#x2139;</span> <!-- the "i" Information icon -->
              </template>
              <template v-else-if="$store.state.testRunner.currentTest === name && (isDebuggable(i) || isResumable(i))">
                <span class="breakpointColumnHeader clickableColumnHeader" title="Clear all breakpoints." @click.stop="removeAllBreakpoints(i)">&#x1F191;</span> <!-- the "i" Information icon -->
              </template>
            </template>
          </template>

          <span v-if="selected === name">
                            <img src="../../assets/arrow-down.png">
          </span>
          <span v-else>
                            <img src="../../assets/arrow-right.png"/>
          </span>
          <span class="large-text">{{ cleanTestName(name) }}</span>
          &nbsp;
          <span v-if="isClient">
                            <button :disabled="running" class="runallbutton" @click.stop="doEval(name)">Run</button>
          </span>
          <span v-else-if="isDebugFeatureEnabled">
                          <template v-if="isPreviousDebuggerStillAttached(i)">
                                <button
                                    @click.stop="removeDebugger(i)"
                                    class="stopDebugTestScriptButton">Remove Debugger</button>
                            </template>
                            <template v-else>
                            <template v-if="formatTestArtifactId(name) in dependencyTestResult">
                                <template v-if="dependencyTestResult[formatTestArtifactId(name)]===true">
                                    <button :disabled="running" v-if="! isResumable(i) && ! isWaitingForBreakpoint" @click.stop="doRun(name, testRoutePath)" class="runallbutton">Run</button>
                                </template>
                                <template v-else>
                                    <span :title="`This test has prerequisite test(s): ${testDependencyList(formatTestArtifactId(name))}. Please run the prerequisite test(s) first.`">&#x1F517;</span>
                                </template>
                            </template>
                            <template v-else>
                                <button :disabled="running" v-if="! isResumable(i) && ! isWaitingForBreakpoint" @click.stop="doRun(name, testRoutePath)" class="runallbutton">Run</button>
                            </template>

                                <template v-if="$store.state.testRunner.currentTest === name">
                                    <button v-if="isDebuggable(i) && ! isWaitingForBreakpoint"
                                            @click.stop="doDebug(name)"
                                            class="debugTestScriptButton"
                                    >Debug</button>
                                    <button v-if="isResumable(i)"
                                            :disabled="isWaitingForBreakpoint"
                                            @click.stop="doDebug(name)"
                                            class="debugTestScriptButtonNormal"
                                    >&#x25B6;&nbsp;Resume</button>
                                    <button v-if="isResumable(i)"
                                            :disabled="isWaitingForBreakpoint"
                                            @click.stop="doStepOver(i)"
                                            class="debugTestScriptButtonNormal"
                                    >&#x2935; Step Over</button>
                                     <button v-if="isResumable(i)"
                                             :disabled="isWaitingForBreakpoint"
                                             title="Continue running and skip all breakpoints."
                                             @click.stop="doFinish(i)"
                                             class="debugTestScriptButtonNormal">&#x23E9; Skip All BPs.</button>
                                    <button v-if="isResumable(i)"
                                            :disabled="isWaitingForBreakpoint"
                                            @click.stop="stopDebugging(i)"
                                        class="debugTestScriptButtonNormal">&#x1F7E5; Stop</button> <!-- &#x270B; -->
                                    <span v-if="isWaitingForBreakpoint">&nbsp;&nbsp;Please wait...&nbsp;&#x23F1;</span>
                                    <!-- Display a stopwatch if waiting for breakpoint to be hit -->
                                </template>
                            </template>
                    </span>
          <span v-else>
              <!-- Unlike the Debug mode, there is no test dependency checks here if Debug mode is disabled.
              If the dependency feature check is required, should create a new Vue component to use in both the Debug-Supported mode and non-Debug mode.  -->
                          <button :disabled="running" @click.stop="doRun(name, testRoutePath)" class="runallbutton">Run</button>
          </span>
          <span v-if="! isWaitingForBreakpoint && ! $store.state.testRunner.isClientTest" title="Test begin time."> --  {{ testTime(name) }}</span>
        </div>
        <debug-assertion-eval-modal v-if="isDebugFeatureEnabled && isEvaluableAction(i)" :show="$store.state.debugAssertionEval.showEvalModalDialog" @close="closeModal()" @resume="doDebug(name)"></debug-assertion-eval-modal>
        <router-view v-if="selected === name"></router-view>  <!--  opens TestOrEvalDetails   -->
      </div>
    </div>
  </div>
</template>

<script>
import testCollectionMgmt from "../../mixins/testCollectionMgmt";
import colorizeTestReports from "../../mixins/colorizeTestReports";
import debugTestScriptMixin from "../../mixins/debugTestScript";
import ScriptStatus from "./ScriptStatus";
import DebugAssertionEvalModal from "./debugger/DebugAssertionEvalModal";
import TestProgressBar from "./TestProgressBar";

export default {
    data() {
        return {
            unSub1: null
        }
    },
    methods: {
      load() {
          console.debug('In TestCollectionBody load' + this.$store.state.testRunner.isUserSuppliedTestFixture + ' ustfKey: ' + this.$store.getters.getUniqueUstfKey )
      /*
       All tests details will be collapsed when loaded.
       The following setCurrentTest to null will reset the expanded arrow indicator
       otherwise the arrow indicator is incorrect when navigating out of the test collection and back into to the same test collection after a test was previously opened.
       */
      this.tcLoading = true
      this.$store.commit('setCurrentTest', null)
      this.loadTestCollection(this.testCollection).then(() => {
          if (this.isClient === false) {
              this.$store.dispatch('debugMgmt', {'cmd': 'getExistingDebuggerList'})
          }
          const testIdParam = this.$router.currentRoute.params['testId']
          if (testIdParam !== undefined && testIdParam !== null) {
              this.$store.commit('setCurrentTest', testIdParam)
          }
      })
    },
    openTest(name) {
      if (!name)
        return
      if (this.selected === name)  { // unselect
        this.$store.commit('setCurrentTest', null)
        const route = `/session/${this.sessionId}/channel/${this.channelName}/collection/${this.testCollection}`
        this.$router.push(route)
        return
      }
      this.$store.commit('setCurrentTest', name)
      const selectedRoutePath = `${this.testRoutePath}/${this.selected}`
      this.$router.push(selectedRoutePath)
    },
      formatTestArtifactId(name) {
       return this.testCollection.concat("/").concat(name)
      },
      testDependencyList(testKey) {
          return this.$store.state.testRunner.ftkTestDependencies[testKey]
      },
  },
  computed: {
    selected() {
      return this.$store.state.testRunner.currentTest
    },
    testRoutePath() {
      const route = `/session/${this.sessionId}/channel/${this.channelName}/collection/${this.testCollection}/test`
      return route
    },
    dependencyTestResult() {
        let returnObj = {}
        const testKeys = Object.keys(this.$store.state.testRunner.ftkTestDependencies)
        const currentTestCollectionTestKeys = testKeys.filter(e => e.startsWith(this.testCollection))
        for (let testKey of currentTestCollectionTestKeys) {
           let passingCt = 0
           const deps = this.$store.state.testRunner.ftkTestDependencies[testKey]
            for (let dep of deps) {
                // TODO: handle "tc/" case
                let tcTr = this.$store.state.testRunner.nonCurrentTcTestReports
                let depName = dep
                if (dep.startsWith(this.testCollection)) {
                    tcTr = this.$store.state.testRunner.testReports
                    depName = dep.split("/")[1]
                }
                if (depName in tcTr) {
                    const tr = tcTr[depName]
                    if (tr !== undefined) {
                        // console.log(`computing ${depName}, tr.result is ${tr.result}, ${testKey}, tcTr count: ${Object.keys(tcTr).length}, deps count: ${deps.length}`)
                       if (tr.result==='pass')
                           passingCt++
                        // else
                        //     console.log(`${depName} failed!`)
                    }
                    // else
                    //     console.log('is undefined!')
                } // else, async func still hasn't loaded it yet?
                // else
                //     console.log(`${depName} does not exist in tcTr! ${Object.keys(tcTr)}`)
            }
            returnObj[testKey] = passingCt > 0 && passingCt === deps.length
        }
        return returnObj
    },
    passingTestCount() {
        return (this.scriptNames.filter(e => this.status[e]==='pass')).length
    },
    failingTestCount() {
          return (this.scriptNames.filter(e => this.status[e]==='fail' || this.status[e]==='error')).length
      },
    notRunTestCount() {
          return (this.scriptNames.filter(e => this.status[e]==='not-run')).length
      },
  },
  created() {
      if (this.$store.state.base.ftkChannelLoaded) {
          this.load(this.testCollection)
          // this.channel = this.channelName
          this.setEvalCount()
      } else {
          console.debug('on Created, ftkChannelLoaded is false, so Loading is deferred.')
      }
  },
  mounted() {
        this.unSub1 = this.$store.subscribe((mutation) => {
            if (mutation.type === 'ftkChannelLoaded') {
                if (this.$store.state.base.ftkChannelLoaded) {
                    // console.log('TestCollectionBody syncing on mutation.type: ' + mutation.type)
                    this.load(this.testCollection)
                    this.setEvalCount()
                }
            }
        })
  },
  beforeDestroy() {
        if (this.unSub1 !== null && this.unSub1 !== undefined)
            this.unSub1()
  },
  watch: {
    'evalCount': 'setEvalCount',
    'testCollection': 'load',
    // 'channelName': function() {
    //   this.load();
    // },
  },
  mixins: [ testCollectionMgmt, colorizeTestReports, debugTestScriptMixin, ],
  name: "TestCollectionBody",
  props: [
    'sessionId', 'channelName', 'testCollection',
  ],
  components: {
    ScriptStatus,
    DebugAssertionEvalModal,
    TestProgressBar,
  }
}
</script>

<style scoped>
</style>
<style>
.clickableColumnHeader,
.infoIcon,
.breakpointColumnHeader {
  position: absolute;
  left: 5px;
  font-size: 14px;
  font-weight: normal;
  text-decoration: none;
  cursor: default;
}
.infoIcon {
  width: 14px;
  border: lightgray 1px solid;
}
.clickableColumnHeader {
  cursor: pointer;
}
.debugTestScriptButtonNormal,
.debugTestScriptButton {
  margin-left: 10px;
  background-color: cornflowerblue;
  cursor: pointer;
  border-radius: 25px;
  font-weight: bold;
}
.debugTestScriptButtonNormal {
  font-weight: normal;
}
.debugFeatureOptionButton {
  margin-left: 7px;
  border-radius: 3px;
  background-color: lavender; /* #FFC83D; */
  font-size: x-small;
}
.testProgressBar {
  display: inline-block;
  text-align: center;
  margin-left: 4px;
}
.timerFont,
.fixedWidthFont {
    font-family: monospace;
    font-weight: lighter;
    font-size: x-small;
    margin-left: 2px;
    horiz-align: center;
    vertical-align: middle;
}
.timerFont {
    border-style: double;
}
</style>
