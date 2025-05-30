<template>
    <div>
        <log-nav v-if="!noNav" :index="index" :sessionId="sessionId" :channelName="channelName" title="Source: FhirChannel Events">
            <template v-if="isLoading"><span>Loading...</span></template>
        </log-nav>

        <div class="boxed">
            <!-- Event Header -->
            <div v-if="eventSummary" class="event-description">
                {{ eventAsDate(eventSummary.eventName) }} - {{ eventSummary.verb}} {{ eventSummary.resourceType }} - {{ eventStatus}}
                <span class="client-server-position">
                <span class="bolded">Client:</span>  {{clientIP}}
                <span class="bolded">Server:</span>  {{channelName}}
            </span>
            </div>

            <div class="request-response">
                <div class="divider"></div>

                <span class="link-position solid-boxed pointer-cursor" @click.stop.prevent="copyToClipboard">Copy Event Link</span>
                <input type="hidden" id="the-link" value="">

                <!-- From Client To Server -->
                <div v-if="selectedEvent">
                    <span title="Normal display of a resource is based on the HAPI FHIR library encode resource to plain text function. Click for known issues with the Contained reference.">
                        <a href="https://github.com/usnistgov/asbestos/wiki/Connectathon-FAQ#issues" target="_blank">Event:</a>&nbsp;
                    </span>&nbsp;
                    <a title="Click for information on Event messages." href="https://github.com/usnistgov/asbestos/wiki/Connectathon-FAQ#event-messages" target="_blank">
                        <img src="../../assets/info.png">
                    </a>
                    <span v-for="(task, taski) in tasks" :key="taski">
                        <span v-bind:class="[{ selected: taski === selectedTask, selectable: taski !== selectedTask }, 'cursor-pointer']" @click="selectTask(taski)">
                            {{ taskLabel(taski) }}
                            <span class="divider"> </span>
                        </span>
                    </span>

                    <a href="https://github.com/usnistgov/asbestos/wiki/Connectathon-FAQ#inspector" target="_blank">
                        <img title="Click for information on Inspector" src="../../assets/info.png">
                    </a>

                    <!-- Request/Response line -->
                    <div>
                        <span class="divider80"></span>
                        <span v-bind:class="{
                            selected: displayRequest,
                            'not-selected': !displayRequest
                         }"
                          @click="displayRequest = true; displayResponse = false; displayInspector = false; displayValidations = false; displayIgValidations = false">
                            Request
                        </span>


                        <div class="divider"></div>
                        <span v-bind:class="{
                            selected: displayResponse,
                            'not-selected': !displayResponse
                            }"
                            @click="displayRequest = false; displayResponse = true; displayInspector = false; displayValidations = false; displayIgValidations = false">
                            Response
                        </span>
                        &nbsp; | &nbsp;
                        <span>
                            <a class="defaultTextColor" title="The raw request body or the raw response body as received by the proxy, displayed without the use of the HAPI FHIR library. The headers may have been processed by the proxy." :href="rawTextEventLink" target="_blank">Raw Message Body</a>
                        </span>
                    </div>

                </div>
                <div v-else>
                    <div class="not-selected" @click="selectedEvent = true; displayRequest = true; displayInspector = false;">Enable display of event messages</div>
                </div>


                <!-- Inspect Validations -->
                <div class="vdivider"></div>
                <div class="vdivider"></div>
                <div class="vdivider"></div>
            </div>

            <div class="request-response">
                <div>
                    Content:
                    <span v-bind:class="{
                        selected: inspectRequest && requestEnabled,
                        'not-selected': !(inspectRequest && requestEnabled)
                        }"
                      @click="doInspectRequest">
                        Inspect Request
                    </span>
                    <div class="divider"></div>
                    <span v-bind:class="{
                        selected: inspectResponse,
                        'not-selected': !(inspectResponse && responseEnabled)
                        }"
                          @click="displayRequest = false; displayResponse = false; displayInspector = true; inspectType = 'response'; displayValidations = false; displayIgValidations = false">
                    Inspect Response/Server
                    </span>
                    <div class="divider"></div>
                    <template v-if="modalMode">
                        <a :href="modalModeEventLink" target="_blank">Validations<img
                                alt="External link" src="../../assets/ext_link.png" style="vertical-align: top"
                                title="Open Inspector in a new browser tab"></a>
                    </template>
                    <template v-else>
                        <span v-bind:class="{
                        'grayText' : isPdbValDisabled,
                         'selected': displayIgValidations,
                         'not-selected': !displayIgValidations,
                         }" @click="doIgVal">
                         IG Validation
                        </span>
                        <select size="1" v-model="igVal">
                            <option :key="eKey"
                                    :value="e.tcName"
                                    v-for="(e,eKey) in getChannelTypeIgTestCollectionArray($store.state.base.channel.channelType)">
                                {{ e.igName }}
                            </option>
                        </select>
                        <span v-bind:class="{
                        'grayText' : isPdbValDisabled,
                         'selected': displayValidations,
                         'not-selected': !displayValidations,
                         }" @click="doPdbVal">
                         Transaction Validation
                        </span>
                        <select size="1" v-model="bundleEvalIgVersion">
                            <option :key="eKey"
                                    :value="e.tcName"
                                    v-for="(e,eKey) in getChannelTypeIgTestCollectionArray($store.state.base.channel.channelType)">
                                {{ e.igName }}
                            </option>
                        </select>

                    </template>
                </div>
            </div>
            <br />
        </div>

        <!-- basic event display choices -->
            <div v-if="!displayInspector && !displayValidations && !displayIgValidations /*&& getEvent()*/">
                <div v-if="displayRequest" class="event-details">
                            <pre>{{ requestHeader }}
                            </pre>
                    <pre>{{ requestBody }}</pre>
                </div>
                <div v-if="!displayRequest" class="event-details">
                            <pre>{{ responseHeader }}
                            </pre>
                    <pre>{{responseBody}}</pre>
                </div>
            </div>

            <!-- extended displays -->
            <div v-if="inspectRequest" class="request-response">
                <log-analysis-report
                        :session-id="sessionId"
                        :channel-name="channelName"
                        :event-id="eventId"
                        :request-or-response="'request'"
                        :no-inspect-label="true"
                        :modal-mode="modalMode"></log-analysis-report>
            </div>
            <div v-if="inspectResponse" class="request-response">
                <log-analysis-report
                        :session-id="sessionId"
                        :channel-name="channelName"
                        :event-id="eventId"
                        :request-or-response="'response'"
                        :no-inspect-label="true"
                        :modal-mode="modalMode"></log-analysis-report>
            </div>
            <div v-if="displayValidations" class="request-response">
                <eval-details
                        :session-id="sessionId"
                        :channel-name="channelName"
                        :event-id="eventId"
                        :test-id="'bundle_eval'"
                        :test-collection="pdbValMhdAssertion"
                        :run-eval="true"
                        :no-inspect-label="true"
                        :modal-mode="modalMode"></eval-details>
            </div>
            <div v-if="displayIgValidations" class="request-response">
                <eval-details
                        :session-id="sessionId"
                        :channel-name="channelName"
                        :event-id="eventId"
                        :test-id="'structure_definition_validation'"
                        :test-collection="igValAssertion"
                        :run-eval="true"
                        :no-inspect-label="true"
                        :modal-mode="modalMode"></eval-details>
            </div>
    </div>
</template>

<script>
    import LogNav from "./LogNav";
    import LogAnalysisReport from "./LogAnalysisReport";
    import {FHIRTOOLKITBASEURL, LOG, PROXY,  UtilFunctions} from '../../common/http-common';
    import eventMixin from '../../mixins/eventMixin';
    import errorHandlerMixin from '../../mixins/errorHandlerMixin';
    import EvalDetails from "../testRunner/EvalDetails";
    import channelMixin from "../../mixins/channelMixin";

    export default {
        data() {
            return {
                selectedEvent: false,
                selectedTask: 0,
                displayRequest: (this.modalMode===undefined)?(this.reqresp==='reqmessage'?true:this.reqresp==='req'):this.modalMode === 'request',
                displayResponse: (this.modalMode===undefined)?(this.reqresp==='respmessage'?true:this.reqresp==='resp'):this.modalMode === 'response',
                displayInspector: false,
                displayValidations: false,
                displayIgValidations: false,
                inspectType: (this.modalMode===undefined)?'request':this.modalMode,
                allEnabled: false,
                isLoading: false,
                // defaultMhdVersion: 'MHDv3.x',
                pdbValMhdAssertion: '',
                igValAssertion: '',
                currentEventSummary: [],
                unSub1 : null,
            }
        },
        methods: {
            eventLink() {
                /*
                const re = new RegExp('(http.*)\\/(session.*)','i')
                console.debug('window.location.href:' + window.location.href)
                const toBe = window.location.href.replace(re, `$1${this.$router.currentRoute.path}`)
                console.debug('toBe:' + toBe)
                return toBe
                */
                // when this is used in computed function, window location path may not be up to date
                // use this is a regular method (non-computed)
                return window.location.href
            },
            copyToClipboard() {
                let linkToCopy =  document.querySelector('#the-link')
                linkToCopy.value = this.eventLink()
                linkToCopy.setAttribute('type', 'text')
                linkToCopy.select()

                try {
                    document.execCommand('copy')
                    this.msg('Copied')
                } catch (error) {
                    this.msg('Cannot copy')
                }


                /* unselect the range */
                linkToCopy.setAttribute('type', 'hidden')
                window.getSelection().removeAllRanges()
            },
            taskLabel(i) {
                if (i === 0)
                    return 'From Client'
                if (i === 1)
                    return 'To Server'
                return i
            },
            selectTask(i) {
                this.selectedTask = i
            },
            selectedEventName() {
                return this.selectedEvent === null ? null : this.selectedEvent.eventName
            },
            getEvent() {
                this.loadEvent()
                return this.selectedEvent
            },
            loadEvent() {
                if (!this.$store.state.log.eventSummaries)
                    return
                const summary = this.$store.state.log.eventSummaries[this.index]
                if (!summary)
                    return
                // don't load if it is already the selected event
                // console.log('summary.eventName = ' + summary.eventName + ' selectedEventName = ' + this.selectedEventName())
                const selectedEventName = summary.eventName === this.selectedEventName() ? null: summary.eventName
                if (selectedEventName !== null) {
                    this.selectedEvent = null
                    this.selectedTask = 0
                    this.isLoading = true
                    // GetEventRequest
                    LOG.get(`${this.sessionId}/${this.channelName}/${summary.resourceType}/${summary.eventName}`)
                        .then(response => {
                            this.isLoading = false
                            try {
                                this.selectedEvent = response.data
                            } catch (error) {
                                this.error(error)
                            }
                        })
                        .catch(error => {
                            this.isLoading = false
                            this.error(error)
                        })

                    // const parms = {
                    //     filterEventId : summary.eventName,
                    //     getSingleEvent: true
                    // }

                    // const url = UtilFunctions.getLogListUrl(parms, this.$store.state.base.channel.testSession, this.$store.state.base.channel.channelName)
                    // console.log('2'+url)

                    try {
                    if (!( 'status' in summary)) {
                        // Now, fully load the event summary for this single event
                        const parms = {
                            filterEventId : summary.eventName,
                            getSingleEvent: true
                        }
                        const methodParms =  {
                            params: {
                                summaries: 'true'
                            }
                        }
                        const url = UtilFunctions.getLogListUrl(parms, this.$store.state.base.channel.testSession, this.$store.state.base.channel.channelName)
                        PROXY.get(url, methodParms)
                            .then((response)=>
                            {
                            // console.log(response.data);
                            this.currentEventSummary.splice(0)
                               response.data.forEach((e,idx) => {
                                   this.currentEventSummary.splice(idx, 1, response.data[idx])
                               })
                            })
                    }
                    } catch (e) {
                        console.error(e)
                    }
                }
            },
            limitLines(text) {
                return text
            },
            translateNewLines(text) {
                return text.replace(/\n/g, '<br />')
            },
            removeFormatting(msg) {
                return msg.replace(/&lt;/g, '<').replace(/&#xa;/g, '\n').replace(/&#x9;/g, '\t')
            },
            async loadEventSummaries() {
                this.isLoading = true
                await this.$store.dispatch('loadEventSummaries',
                    {testSession: this.sessionId, channel: this.channelName, filterEventId: this.eventId})
                    .then(() => this.isLoading = false)
                this.getEvent()
            },
            setupMyComponent() {
                this.loadEventSummaries()
                if (this.requestEnabled && !this.responseEnabled) {
                    this.displayInspector = true;
                    this.inspectType = 'request';
                }
                if (!this.requestEnabled && this.responseEnabled) {
                    this.displayInspector = true;
                    this.inspectType = 'response';
                }
            },
            doInspectRequest() {
                this.displayRequest = false;
                this.displayResponse = false;
                this.displayInspector = true;
                this.inspectType = 'request';
                this.displayValidations = false
                this.displayIgValidations = false
            },
            doPdbVal() {
                if (!this.isPdbValDisabled) {
                    // console.log(this.bundleEvalIgVersion)
                    if (this.modalMode === undefined || this.modalMode==='') {
                        this.$store.commit('setTestCollectionName', this.bundleEvalIgVersion)
                        this.$store.dispatch('runSingleEventEval',
                            {
                                testId: 'bundle_eval',
                                eventId: this.eventId,
                                testCollectionName: this.bundleEvalIgVersion
                            }).then(() => {
                            this.$store.dispatch('loadTestScripts', ['bundle_eval']).then(() => {
                                this.displayRequest = false;
                                this.displayResponse = false;
                                this.displayInspector = false;
                                this.inspectType = '';
                                this.displayValidations = true;
                                this.displayIgValidations = false;
                            })
                        })
                    }

                }
            },
            doIgVal() {
                    // console.log(this.bundleEvalIgVersion)
                    if (this.modalMode === undefined || this.modalMode==='') {
                        this.$store.commit('setTestCollectionName', this.igVal)
                        this.$store.dispatch('runSingleEventEval',
                            {
                                testId: 'structure_definition_validation',
                                eventId: this.eventId,
                                testCollectionName: this.igVal
                            }).then(() => {
                            this.$store.dispatch('loadTestScripts', ['structure_definition_validation']).then(() => {
                                this.displayRequest = false;
                                this.displayResponse = false;
                                this.displayInspector = false;
                                this.inspectType = '';
                                this.displayValidations = false;
                                this.displayIgValidations = true;
                            })
                        })
                    }
            },

        },
        computed: {
            eventStatus() {
                let myEventStatus = null
                if ('status' in this.eventSummary) {
                    myEventStatus = this.eventSummary.status
                } else {
                    if (this.currentEventSummary !== null && this.currentEventSummary.length > 0 && this.eventSummary !== null) {
                        var o = this.currentEventSummary.filter(
                            e => e.resourceType === (this.eventSummary.resourceType) && e.eventName === (this.eventSummary.eventName))
                        if (o !== undefined && o.length > 0) {
                        myEventStatus = o[0].status
                        }
                    }
                }
                if (myEventStatus !== null) {
                    return myEventStatus ? 'Ok' : 'Error'
                }
                return ''
            },
            isPdbValDisabled() {
                return this.pdbValMhdAssertion==='' || this.pdbValMhdAssertion===undefined || this.inspectType=== 'response'
            },
            requestEnabled() {
                if (this.allEnabled) return true;
                if (!this.reqresp)
                    return false;
                return this.reqresp === 'req';
            },
            responseEnabled() {
                if (this.allEnabled) return true;
                if (!this.reqresp)
                    return false;
                return this.reqresp === 'resp';
            },
            inspectRequest() {
                return this.displayInspector && this.inspectType === 'request'
            },
            inspectResponse() {
                return this.displayInspector && this.inspectType === 'response'
            },
            clientIP() {
                const idx = this.index
                return idx ? this.$store.state.log.eventSummaries[idx].ipAddr : null
            },
            index() {  // of eventId
                return (this.$store.state.log.eventSummaries)
                    ? this.$store.state.log.eventSummaries.findIndex(summary => this.eventId === summary.eventName)
                    : null
            },
            tasks() {
                return this.selectedEvent && this.selectedEvent.tasks !== undefined ? this.selectedEvent.tasks : []
            },
            taskCount() {
                return this.selectedEvent && this.selectedEvent.tasks !== null && this.selectedEvent.tasks !== undefined ? this.selectedEvent.tasks.length : 0
            },
            requestHeader() {
                if (this.taskCount)
                    return this.selectedEvent.tasks[this.selectedTask].requestHeader
                return null
            },
            requestBody() {
                if (this.taskCount)
                     return this.removeFormatting(this.limitLines(this.selectedEvent.tasks[this.selectedTask].requestBody))
                return null
            },
            responseHeader() {
                if (this.taskCount)
                    return this.selectedEvent.tasks[this.selectedTask].responseHeader
                return null
            },
            responseBody() {
                if (this.taskCount)
                    return this.removeFormatting(this.limitLines(this.selectedEvent.tasks[this.selectedTask].responseBody))
                return null
            },
            eventSummary() {
                if (!this.$store.state.log.eventSummaries)
                    return null
                const eSummary = (this.index > -1)
                    ? this.$store.state.log.eventSummaries[this.index]
                    : null

                return eSummary
            },
            modalModeEventLink() {
                // Example http://localhost:8082/session/default/channel/limited/lognav/
                let reqStr = (this.inspectType=='request') ? 'req' : 'resp'
               return '/session/' + this.sessionId +'/channel/' + this.channelName + '/lognav/'  + this.eventId + '/' + reqStr
            },
            rawTextEventLink() {
                const summary = this.$store.state.log.eventSummaries[this.index]
                if (!summary) {
                    console.log('rawTextEvent Error: No Summary.');
                    return
                }
                const url = FHIRTOOLKITBASEURL + '/log/' + this.sessionId + '/' + this.channelName + '/' + summary.resourceType + '/' + this.eventId + '?textMode=raw'
                return url
            },
            bundleEvalIgVersion: {
                set(val) {
                      if (val === '' || val === undefined)
                          return;
                      this.pdbValMhdAssertion= val
                    this.doPdbVal()
                },
                get() {
                    return this.pdbValMhdAssertion
                }
            },
            igVal: {
                set(val) {
                      if (val === '' || val === undefined)
                          return;
                      this.igValAssertion = val
                    this.doIgVal()
                },
                get() {
                    return this.igValAssertion
                }
            }
        },
        created() {
            if (this.$store.state.base.ftkChannelLoaded) {
                this.setupMyComponent()
            }
        },
        mounted() {
            this.unSub1 = this.$store.subscribe((mutation) => {
                if (mutation.type === 'ftkChannelLoaded') {
                    if (this.$store.state.base.ftkChannelLoaded) {
                        this.setupMyComponent()
                    }
                }
            })
        },
        beforeDestroy() {
            if (this.unSub1 !== null && this.unSub1 !== undefined)
                this.unSub1()
        },
        watch: {
            eventId() {
                if (this.noNav)
                    return
                this.loadEvent()
            },
            modalMode: function(newVal) {
                /* this is required to react to changes in the FHIRPath modal fixture dropdown selection */
                if (newVal === 'request') {
                    this.displayResponse = false
                    this.displayRequest = true
                    this.inspectType = newVal
                } else if (newVal === 'response') {
                   this.displayRequest = false
                   this.displayResponse = true
                   this.inspectType = newVal
                }
            },
        },
        props: [
            'eventId', 'sessionId', 'channelName', 'noNav', 'reqresp', 'modalMode',
        ],
        mixins: [eventMixin, errorHandlerMixin, channelMixin],
        components: {
            LogNav,
            LogAnalysisReport,
            EvalDetails
        },
        name: "InspectEvent"
    }
</script>

<style scoped>
    .boxed {
        border: 1px solid rgba(0, 0, 0, 0.8);
        position: relative;
        /*left: -60px;*/
    }
    .bolded {
        font-weight: bold;
    }
    .client-server-position {
        font-weight: normal;
        position: absolute;
        left: 600px;
    }
    .event-details {
        text-align: left;
        overflow: scroll;
        height: 400px;
        border: 1px solid black;
    }
    .event-description {
        font-weight: bold;
        text-align: left;
    }
    .request-response {
        position: relative;
        left: 60px;
        text-align: left;
    }
    .selected {
        font-weight: bold;
        cursor: pointer;
        text-decoration: underline;
    }
    .right {
        text-align: right;
    }
    .link-position {
        position: absolute;
        left: 350px;
    }
    .not-selected {
        cursor: pointer;
        text-decoration: underline;
    }
    .defaultTextColor {
        color: black;
    }
</style>
