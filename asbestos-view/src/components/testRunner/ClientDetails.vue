<template>  <!-- once for each client test-->
    <div v-if="testScript" class="align-left test-margins">

        <script-status :name="testId" :event-id="eventId"> </script-status>

        <span v-if="$store.state.testRunner.currentEvent === eventId">
            <img src="../../assets/arrow-down.png">
        </span>
        <span v-else>
            <img src="../../assets/arrow-right.png"/>
        </span>

        <span  @click.self="selectEvent()" class="event-part" v-bind:class="[isEventPass() ? passClass : failClass]">
            Message: {{ eventId }} - {{ eventDetail }}
        </span>

        <div v-if="currentEvent === eventId">
            <!-- wants only the action part of report -->
            <div v-if="primaryTestReport">
                <script-details
                        :script="testScript"
                        :report="primaryTestReport"
                        :disable-debugger="'true'"> </script-details>
            </div>
            <div v-else>
                <script-details
                        :script="testScript"
                        :report="testReport"
                        :disable-debugger="'true'"> </script-details>
            </div>
        </div>
    </div>
</template>

<script>
import colorizeTestReports from "../../mixins/colorizeTestReports";
import ScriptStatus from "./ScriptStatus";
import ScriptDetails from "./ScriptDetails";
// import {UtilFunctions } from "../../common/http-common";

    export default {
    data() {
            return {
                passClass: null,  // initialized in created()
                failClass: null,
                //primaryTestReport: null,
            }
        },
        methods: {
        /*
            eventDetail() {
                const parms = {
                    filterEventId : this.eventId,
                    getSingleEvent: true
                }

                const url = UtilFunctions.getLogListUrl(parms, this.$store.state.base.channel.testSession, this.$store.state.base.channel.channelName)
                const methodParams =
                    {
                        params: {
                            summaries: 'true'
                        }
                    }
                PROXY.get(url, methodParams)
                    .then((response)=>
                    {
                        // console.log(response.data);
                        this.currentEventSummary.splice(0)
                        response.data.forEach((e,idx) => {
                            // this.currentEventSummary.splice(idx, 1, response.data[idx])
                            const summary = response.data[idx]
                            return `${summary.verb} ${summary.resourceType} from ${summary.ipAddr}`
                        })
                    })
                return null
            },
            */

            selectEvent() {
                // currentEvent is this.$store.state.testRunner.currentEvent
                // eventId is always set (passed from parent)
                if (this.currentEvent === this.eventId)  { // unselect
                    // this.primaryTestReport = null;
                    this.$store.commit('setCurrentEvent', null)
                    const currentRoutePath = this.$router.currentRoute.path
                    const routePathToBe = `/session/${this.sessionId}/channel/${this.channelName}/collection/${this.testCollection}/test/${this.testId}`
                    // console.log('current route: ' + currentRoutePath)
                    // console.log('route to be: ' + routePathToBe)
                    if (currentRoutePath !== routePathToBe) {
                        this.$router.push(routePathToBe)
                    }
                } else {
                    this.$store.commit('setCurrentEvent', this.eventId)
                    // If this is a client test then testReport is an array.
                    // Parse this into the primary (put into primaryTestReport) and secondaries (modules).
                    // Module reports are put into testRunner.moduleTestReports.
                    // ScriptDetails expects this partitioning.
                    if (Array.isArray(this.testReport)) {
                        // if (this.testReport.length > 0)
                        //     this.primaryTestReport = this.testReport[0];
                        // else
                        //     this.primaryTestReport = null;
                        let moduleReports = {};
                        for (let i = 1; i < this.testReport.length; i++) {
                            const report = this.testReport[i];
                            const name = report.name;
                            moduleReports[name] = report;
                        }
                        this.$store.commit('setModuleTestReports', moduleReports);
                    }

                    const route = `/session/${this.sessionId}/channel/${this.channelName}/collection/${this.testCollection}/test/${this.testId}/event/${this.eventId}`
                    this.$router.push(route)
                }
            },
            isEventPass() {
                return this.eventResult[this.eventId][0].result === 'pass'
            },
            selectCurrent() {
                this.selectEvent(this.selected)
            },
            loadTest() {
                if (!this.$store.state.testRunner.testScripts[this.testId])
                    this.$store.dispatch('loadTestScript', { testCollection: this.testCollection, testId: this.testId })
            },
        },
        computed: {
            eventDetail() {
                /*
                        if (this.logSummariesNeedLoading || this.logSummariesNeedLoading2) {
                            const parms = {
                                filterEventId : this.eventId,
                                getSingleEvent: true
                            }
                            this.$store.dispatch('loadEventSummaries', parms)
                        }
                 */
                try {
                        if (this.$store.state.log.eventSummaries.length > 0) {
                            const summary = this.$store.state.log.eventSummaries.find(it =>
                                it.eventName === this.eventId)
                            if (summary)
                                return `${summary.verb} ${summary.resourceType} from ${summary.ipAddr}`
                        } else
                            return 'Loading...'
                } catch {
                    return "Exception"
                }
                return ''
            },

            primaryTestReport() {
                  return (this.testReport && this.testReport.length > 0)
                      ? this.testReport[0]
                      : null;
            },
            isPass() {
                return this.eventResult[this.eventId].result === 'pass'
            },
            isFail() {
                return this.eventResult[this.eventId].result === 'fail'
            },
            /*
            logSummariesNeedLoading() {  // because of channel change
                return !this.$store.state.log.eventSummaries ||
                    this.sessionId !== this.$store.state.log.session ||
                        this.channelName !== this.$store.state.log.channel
            },
            logSummariesNeedLoading2() {  // because there are eventIds not present in summaries
                if (!this.eventIds) return false
                const lastEventId = this.eventIds[0]
                if (this.$store.state.log.eventSummaries.length === 0) return true
                const lastSummaryId = this.$store.state.log.eventSummaries[0].eventName
                return lastEventId > lastSummaryId
            },
             */
            testScript() {
                return this.$store.state.testRunner.testScripts[this.testId]
            },
            currentEvent() {
                return this.$store.state.testRunner.currentEvent
            },
            eventIds() {
                if (!this.eventResult) {
                    return null;
                }
                return Object.keys(this.eventResult).sort().reverse()
            },
            eventResult() {  // returns array of reports (primary and any modules)
                return this.$store.state.testRunner.clientTestResult[this.testId]
            },
            testReport() {  // should return primary only
                if (!this.currentEvent)
                    return null;
                return this.eventResult[this.currentEvent];
            }
        },
        created() {
            if (this.$store.state.testRunner.colorMode) {
                this.passClass = 'pass'
                this.failClass = 'fail'
            } else {
                this.passClass = 'pass-plain-detail'
                this.failClass = 'fail-plain-detail'
            }
            this.loadTest()
        },
        watch: {
            'testId': 'loadTest',
        },
        props: [
            'sessionId', 'channelName', 'testCollection', 'testId', 'eventId'
        ],
        components: {
            ScriptStatus, ScriptDetails
        },
        mixins: [colorizeTestReports],
        name: "ClientDetails"
    }
</script>

<style scoped>
    .event-part {
        /*margin-left: 5px;*/
        /*margin-right: 15px;*/
        cursor: pointer;
        /*text-decoration: underline;*/
    }
</style>
