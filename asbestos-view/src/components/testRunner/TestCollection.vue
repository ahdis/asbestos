<template>
  <div>
  <template v-if="testCollection !== '' && testCollection !== undefined && testCollection !== null">
    <test-collection-header
        :test-collection="testCollection"
        :session-id="sessionId"
        :channel-name="channelName"> </test-collection-header>
    <test-collection-body
        :test-collection="testCollection"
        :session-id="sessionId"
        :channel-name="channelName"> </test-collection-body>
  </template>
    <template v-else>
      Please select a Test Collection.
    </template>
  </div>
</template>

<script>
import errorHandlerMixin from '../../mixins/errorHandlerMixin'
import colorizeTestReports from "../../mixins/colorizeTestReports";
import TestCollectionHeader from "./TestCollectionHeader";
import TestCollectionBody from "./TestCollectionBody";
import testCollectionMgmt from "../../mixins/testCollectionMgmt";

export default {

  data() {
    return {
      time: [],   // built as a side-effect of status (computed)
      // isLoading: false,
    }
  },
  methods: {
    hasSuccessfulEvent(testId) {
      if (testId === null)
        return false
      const eventResult = this.$store.state.testRunner.clientTestResult[testId]
      for (const eventId in eventResult) {
        if (eventResult.hasOwnProperty(eventId)) {
          const testReport = eventResult[eventId]
          if (testReport.result === 'pass')
            return  true
        }
      }
      return false
    },
    testReport(testName) {
      if (!testName)
        return null
      return this.$store.state.testRunner.testReports[testName]
    },

  },
  computed: {
  },
  created() {
  },
  mounted() {
  },
  watch: {
  },
  mixins: [ errorHandlerMixin, colorizeTestReports, testCollectionMgmt ],
  name: "TestCollection",
  props: [
    'sessionId', 'channelName', 'testCollection',
  ],
  components: {
    TestCollectionHeader, TestCollectionBody,
  }
}
</script>

<style scoped>
.banner-color {
  background-color: lightgray;
  text-align: left;
}
.configurationError {
  color: red;
}
</style>
<style>
.runallbutton {
  /*padding-bottom: 5px;*/
  background-color: cornflowerblue;
  cursor: pointer;
  border-radius: 25px;
  font-weight: bold;
}
.clearLogsButton {
  background-color: seashell;
  cursor: pointer;
  border-radius: 25px;
  font-weight: normal;
  margin-left: 8px;
  margin-right: 8px;
}
.button-selected {
  border: 1px solid black;
  background-color: lightgray;
  cursor: pointer;
}
.button-not-selected {
  border: 1px solid black;
  cursor: pointer;
}
.runallgroup {
  text-align: right;
  padding-bottom: 5px;
}
.running {
    margin-left: 3px;
  background-color: lightgreen;
}
.tcLoading {
  background-color: orange;
}
.conformance-tests-header {
  background-color: #DBD9BE;
}
.stopDebugTestScriptButton {
  /*padding-bottom: 5px;*/
  margin-left: 10px;
  background-color: red;
  cursor: pointer;
  border-radius: 25px;
  font-weight: normal;
}
.configurationError {
  color: red;
}
.noListStyle {
  list-style-type: none;
}
.pre-test-gap{
  height:1px;
  width:auto;
}
.large-text {
  font-size: large;
}
.pass {
  background-color: lightgreen;
  text-align: left;
  border: 1px dotted black;
  cursor: pointer;
  border-radius: 25px;
}
.pass-plain {
  /*background-color: lightgray;*/
  text-align: left;
  border-top: 1px solid black;
  /*border-bottom: 1px solid black;*/
  cursor: pointer;
  /*border-radius: 25px;*/
}
.pass-plain-header {
  text-align: left;
  border-top: 1px solid black;
  cursor: pointer;
}
.pass-plain-detail {
  margin-bottom: 2px;
  text-align: left;
  cursor: pointer;
}
.fail {
  background-color: indianred;
  text-align: left;
  border: 1px dotted black;
  cursor: pointer;
  border-radius: 25px;
}
.fail-plain {
  /*background-color: lightgray;*/
  text-align: left;
  /*border-top: 1px solid black;*/
  /*border-bottom: 1px solid black;*/
  cursor: pointer;
  /*border-radius: 25px;*/
}
.fail-plain-header {
  text-align: left;
  border-top: 1px solid black;
  cursor: pointer;
}
.fail-plain-detail {
  margin-bottom: 2px;
  text-align: left;
  cursor: pointer;
}
.condition-fail {
  background-color: gold;
  text-align: left;
  border: 1px dotted black;
  cursor: pointer;
  border-radius: 25px;
}
.error-plain {
  /*background-color: cornflowerblue;*/
  text-align: left;
  /*border-top: 1px solid black;*/
  cursor: pointer;
  /*border-radius: 25px;*/
}
.error {
  background-color: cornflowerblue;
  text-align: left;
  border: 1px dotted black;
  cursor: pointer;
  border-radius: 25px;
}
.not-run {
  background-color: lightgray;
  text-align: left;
  border: 1px dotted black;
  cursor: pointer;
  border-radius: 25px;
}
.not-run-plain {
  text-align: left;
  border-top: 1px solid black;
  cursor: pointer;
}
.not-run-plain-detail {
  margin-bottom: 2px;
  text-align: left;
  cursor: pointer;
}

.align-right {
  text-align: right;
}
.align-left {
  text-align: left;
}
.noTopMargin {
  margin-top: 0px;
}
.grayText {
  color: gray;
}
.testBarMargin {
  margin-bottom: 3px;
}
.inlineDiv {
  display: inline;
}
.loading {
  /*color: #f5f5f5;*/
  font-size: medium;
  font-weight: bolder;
  display: block;
  visibility: visible;
}
</style>
