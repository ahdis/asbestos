<template>
    <span>
        <span v-if="statusOnRight">
            <span v-if="isPass">
                <img src="../../assets/checked.png" class="align-right">
            </span>
            <span v-else-if="isFail">
                <img src="../../assets/error.png" class="align-right">
            </span>
            <span v-else-if="isError">
                <img src="../../assets/yellow-error.png" class="align-right">
            </span>
            <span v-else>
                <img src="../../assets/blank-circle.png" class="align-right">
            </span>
        </span>
        <span v-if="!statusOnRight">
            <span v-if="isConditional">
                <span v-if="isPass">
                    <img src="../../assets/like.png" class="align-left">
                </span>
                <span v-else-if="isFail">
                    <img src="../../assets/thumb-down.png" class="align-left">
                </span>
                <span v-else-if="isError">
                    <img src="../../assets/yellow-error.png" class="align-left">
                </span>
                <span v-else>
                    <img src="../../assets/blank-circle.png" class="align-left">
                </span>
            </span>
            <span v-else-if="isExpectFailure">
                <span v-if="isPass">
                    <img src="../../assets/select.png" class="align-left" title="Pass if expected assertion failed (check the top-level TestScript, under the test Run button, for the expectedFailAssertionIdList variable). Fails if an unexpected assertion failed.">
                </span>
                <span v-else-if="isFail">
                    <img src="../../assets/error.png" class="align-left" title="Unexpected assertion failure. Check the top-level TestScript, under the test Run button, for the expectedFailAssertionIdList variable.">
                </span>
                <span v-else-if="isError">
                    <img src="../../assets/yellow-error.png" class="align-left">
                </span>
                <span v-else>
                    <img src="../../assets/blank-circle.png" class="align-left">
                </span>
            </span>
            <span v-else-if="isManualReviewRequired">
                <span v-if="isPass">
                    <img src="../../assets/checked.png" class="align-left" title="No errors.">
                </span>
                <span v-else-if="hasWarningOperation">
                    <img src="../../assets/round.png" class="align-left" title="Manually review the OperationOutcome resource for Issues.">
                </span>
                <span v-else-if="isFail">
                    <img src="../../assets/error.png" class="align-left" title="">
                </span>
                <span v-else-if="isError">
                    <img src="../../assets/yellow-error.png" class="align-left" title="Has unexpected error(s).">
                </span>
                <span v-else>
                    <img src="../../assets/blank-circle.png" class="align-left" title="Not run.">
                </span>
            </span>
            <span v-else>
                <span v-if="isPass">
                    <img src="../../assets/checked.png" class="align-left">
                </span>
                <span v-else-if="isTrue">
                    <img src="../../assets/like.png">
                </span>
                <span v-else-if="isFail">
                    <img src="../../assets/error.png" class="align-left">
                </span>
                <span v-else-if="isFalse">
                    <img src="../../assets/thumb-down.png" class="align-left">
                </span>
                <span v-else-if="isError">
                    <img src="../../assets/yellow-error.png" class="align-left">
                </span>
                <span v-else>
                    <img src="../../assets/blank-circle.png" class="align-left">
                </span>
            </span>
        </span>
    </span>
</template>

<script>
    import colorizeTestReports from "../../mixins/colorizeTestReports";

    export default {
        computed: {
            isConditional() {
                if (!this.script) return false;
                if (!this.script.modifierExtension) return false;
                let cond = false;
                this.script.modifierExtension.forEach(ex => {
                    if (ex.url === 'https://github.com/usnistgov/asbestos/wiki/TestScript-Conditional')
                        cond = true;
                })
                return cond;
            },
            isExpectFailure() {
                if (this.report === null || this.report===undefined) return false;
                if (!this.report.action === null || this.report.action === undefined || this.report.action.length < 1) return false;
                if (!this.report.action[0].modifierExtension === null || this.report.action[0].modifierExtension === undefined) return false;
                let expectFailure = false;
                this.report.action[0].modifierExtension.forEach(ex => {
                    if (ex.url === 'urn:asbestos:test:action:expectFailure')
                        expectFailure = true;
                })
                return expectFailure;
            }
        },
        props: [
            'statusOnRight', 'script', 'report'
        ],
        mixins: [colorizeTestReports],
        name: "TestStatus"
    }
</script>

<style scoped>

</style>
