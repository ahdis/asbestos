<template>
    <div>
        <div class="control-panel-item-title">Channels</div>
        <div>
            <template v-if="isCanAddChannel || editUserProps.signedIn">
                <div class="tooltip">
                    <img @click="addBtnClick()" id="add-button" src="../../assets/add-button.png"/>
                    <span class="tooltiptext">Add Channel</span>
                </div>
            </template>
            <template v-else>
                <div class="tooltip">
                    <img class="dimOpacity" disabled="disabled" src="../../assets/add-button.png"/>
                    <span class="tooltiptext">Channel create is disabled in this test session</span>
                </div>
            </template>

            <template v-if="isCanRemoveChannel || editUserProps.signedIn">
                <div class="tooltip">
                    <img @click="guardedFn('Delete',del)" id="delete" src="../../assets/exclude-button-red.png"/>
                    <span class="tooltiptext">Delete Channel</span>
                </div>
            </template>
            <template v-else>
                <div class="tooltip">
                    <img class="dimOpacity" disabled="disabled" src="../../assets/exclude-button-red.png"/>
                    <span class="tooltiptext">Remove channel is disabled in this test session</span>
                </div>
            </template>


            <div class="tooltip">
                <button @click="manage()" type="button">Config</button>
                <span class="tooltiptext">Config</span>
            </div>
        </div>
        <select :disabled="disabled" class="control-panel-list control-panel-font" size="10" v-model="cpChannelName">
            <option :key="chann + channeli"
                    v-bind:value="chann"
                    v-for="(chann, channeli) in channelNames"
            >
                {{ chann }}
            </option>
        </select>
        <div v-if="adding">
            <input v-model="newChannelName">
            <button @click="doAdd()">Add</button>
            <button @click="cancelAdd()">Cancel</button>
        </div>
        <div v-else-if="deleting">
            <p>
                Are you sure you want to delete {{channelId}}?
            </p>
            <div>
                <button @click="confirmDel()">Delete</button>
                <button @click="cancelDel()">Cancel</button>
            </div>
        </div>
        <div v-else-if="lockAckMode">
            <sign-in :banner="lockAckMode" :doDefaultSignIn="true" :showCancelButton="true"
                     :userProps="editUserProps" @onCancelClick="cancelDel" @onOkClick="lockAcked"/>
        </div>
    </div>
</template>

<script>
    import Vue from 'vue'
    import {BFormSelect} from 'bootstrap-vue'

    Vue.component('b-form-select', BFormSelect)
    import {newChannel} from '@/types/channel'
    import {ASBTS_USERPROPS, PROXY} from "@/common/http-common";
    import SignIn from "../SignIn";
    import channelMixin from '@/mixins/channelMixin'
    import testSessionMixin from "../../mixins/testSessionMixin";

    export default {
        data() {
            return {
                // channelNames: [],
                lockAckMode: false,  // for deleting
                adding: false,
                newChannelName: null,
                // channel: null, // used only for adding a new channel
                deleting: false,
                editUserProps: ASBTS_USERPROPS,
                unSub1: null,
            }
        },
        components: {
            SignIn,
        },

        methods: {
            setupMyComponent() {
                const paramChannelName = this.$router.currentRoute.params['channelName']
                // console.log(`channelName prop: ${paramChannelName}`)
                if (paramChannelName === undefined) {
                    const defaultChannelId = 'default__default'
                    if (this.channelNames.includes(defaultChannelId) || (this.sessionId === 'default' && this.channelNames.includes('default'))) {
                        this.cpChannelName = defaultChannelId
                    } else if (this.channelNames.length > 0) {
                        this.cpChannelName = this.channelNames[0]
                    }
                } else {
                    const paramSessionId = this.$router.currentRoute.params['sessionId']
                    this.cpChannelName = paramSessionId + '__' + paramChannelName
                }
            },
            /*
            updateChannelLockStatus(ch) {
                if (ch !== undefined && typeof (ch) === 'object' && ch['writeLocked'] !== undefined) {
                    this.channel.writeLocked = ch.writeLocked
                }
            },
            */
            lockAcked() {
            },
            lockCanceled() {
                this.lockAckMode = ""
                this.lockAcked = null
                this.deleting = false
            },
            async confirmDel() {
                try {
                    if (!this.theChannel.writeLocked) {
                        await PROXY.delete('rw/channel/' + this.channelId)
                    } else if (this.editUserProps.bapw !== "") {
                        await PROXY.delete('accessGuard/channel/' + this.channelId, {
                            auth: {
                                username: this.editUserProps.bauser,
                                password: this.editUserProps.bapw
                            }
                        })
                    }
                    this.msg('Deleted.')
                    // this.localDelete(channelId);
                    this.$store.commit('deleteChannel', this.channelId)
                    this.$store.commit('setChannelName', undefined)
                    this.$store.commit('setChannel', undefined)
                    await this.$store.dispatch('loadChannelIds')
                    // this.setChannelId();
                    this.$router.push('/session/' + this.sessionId + '/channels')
                } catch (error) {
                    this.error(error);
                }
                this.adding = false;
                this.cancelDel()
            },
            cancelDel() {
                this.deleting = false;
                this.lockAcked = null;
                this.lockAckMode = "";
            },

            del() {
                this.deleting = true;
            },

            guardedFn(str, fn) {
                if ('Delete' === str) {
                    this.adding = false;
                }
                if (typeof fn === 'function') {
                    if (this.theChannel.writeLocked) {
                        if (this.editUserProps.signedIn) {
                            this.lockAcked = null
                            fn.call()
                        } else {
                            const that = this
                            this.lockAcked = function () {
                                that.guardedFn(str, fn)
                            }
                            this.lockAckMode = str + ": "
                        }
                    } else {
                        fn.call()
                    }
                }
            },
            addBtnClick() {
                this.newChannelName = null
                this.cancelDel()
                this.adding = true
            },
            doAdd() {
                const channel = newChannel();
                channel.channelName = this.newChannelName;
                if (this.isCurrentChannelIdBadPattern(channel)) {
                    this.$store.commit('setError', `Name may only contain a-z A-Z 0-9 and -.`);
                    return;
                }
                channel.testSession = this.sessionId;
                //this.$store.commit('setChannel', this.channel)
                this.adding = false;
                // const channelId = `${this.sessionId}__${this.newChannelName}`
                // if (this.channelIds === null)
                //     this.channelIds = [];
                //this.channelIds.push(this.channelId);
                this.$store.commit('setChannelIsNew', true);
                this.$store.commit('installChannel', channel);
                // this.$store.commit('setChannelIsNew');
                this.pushChannelRoute(channel);
            },
            isCurrentChannelIdBadPattern(ch) {
                const name = ch.channelName
                const re = RegExp('^([a-zA-Z0-9-]+)$')
                const match = re.test(name)
                const re2 = RegExp('.*__.*')
                const match2 = re2.test(name)
                return !match || match2
            },
            cancelAdd() {
                this.newChannelName = null;
                this.adding = false;
            },
            updateChannelIds() {
                this.channelIds = this.$store.getters.getChannelIdsForCurrentSession;
            },
            // for create a new channel
            pushChannelRoute(ch) {
                const route = this.channelRoute(ch);
                if (route)
                    return this.$router.push(route);
            },
            channelRoute(ch) {
                if (ch===undefined || ch===null)
                    return null;
                return '/session/' + ch.testSession + '/channels/' + ch.channelName;
            },
            channelsLink(channelId) {
                const chan = channelId.split('__', 2);
                const session = chan[0];
                const myChannelName = chan[1];
                return '/session/' + session + '/channels/' + myChannelName;
            },
            msg(msg) {
                console.log(msg)
                this.$bvToast.toast(msg, {noCloseButton: true})
            },
            error(err) {
                this.$bvToast.toast(err.message, {noCloseButton: true, title: 'Error'})
                console.log(err)
            },

            manage() {  // go edit channel definitions
                if (this.$store.state.base.channelName !== undefined && this.$store.state.base.channel !== undefined && this.$store.state.base.channel !== null) {
                    const r = `/session/${this.$store.state.base.channel.testSession}/channels` + `/${this.$store.state.base.channel.channelName}`
                    const currentRoutePath = this.$router.currentRoute.path
                    if (currentRoutePath != r) {
                        // console.debug(r)
                        this.$router.push(r)
                    }
                }
            },
            channelValid(channelId) {
                if (!channelId)
                    return false;
                const parts = channelId.split('__', 2);
                return !(parts.length !== 2 || parts[0] === null || parts[1] === null)
            },
        },
        computed: {
            channelNames() {
                return this.$store.getters.getChannelNamesForCurrentSession
            },
            channelId() {
                return `${this.theChannel.testSession}__${this.theChannel.channelName}`
            },
            sessionId() {
                return this.$store.state.base.session
            },
            cpChannelName: {
                set(name) {
                    if (name === undefined) return
                    if (name !== this.$store.state.base.channel.channelName) {
                        this.ftkLoadChannel(name, true, true)
                    }
                },
                get() {
                    const channelTestSession = this.$store.state.base.channel.testSession
                    const myChannelName = this.$store.state.base.channel.channelName
                    if (this.$store.state.base.session !== channelTestSession) {
                        return channelTestSession + '__' + myChannelName
                    } else {
                       return myChannelName
                    }
                }
            },
            theChannel() {
                return this.$store.state.base.channel
            },
        },
        created() {
        },
        mounted() {
            this.unSub1 = this.$store.subscribe((mutation) => {
                if (mutation.type === 'ftkInitComplete') {
                    if (this.$store.state.base.ftkInitialized) {
                            // console.log('ChannelCP syncing on mutation.type: ' + mutation.type)
                            this.setupMyComponent()
                        }
                }
            })

        },
        beforeDestroy() {
            if (this.unSub1 !== null && this.unSub1 !== undefined)
                this.unSub1()
        },
        props: [
            'disabled'
        ],
        watch: {},
        mixins: [channelMixin, testSessionMixin],
        name: "ChannelControlPanel"
    }
</script>

<style scoped>
</style>
