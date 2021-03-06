/*
 * Copyright 2014 Intelix Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
define(['react', 'coreMixin', 'subscriberMixin', 'admin/agent/datatap/List'],
    function (React, coreMixin, subscriberMixin, List) {

    return React.createClass({
        mixins: [coreMixin, subscriberMixin],

        getInitialState: function () {
            return {}
        },


        subscriptionConfig: function () {
            return {address: this.props.addr, route: this.props.id, topic: 'info', target: 'info'};
        },

        componentWillUpdate: function(nextProps, nextState) {
            if (!this.state.info_stale && nextState.info_stale) {
                this.raiseEvent("tapUnavailable", {id: this.props.id});
            }
        },

        onUnmount: function() {
            console.debug("!>>>> hm1");
            this.raiseEvent("tapUnavailable", {id: this.props.id});
        },


        render: function () {
            return (
                <div>
                    <List {...this.props} id={this.props.id}/>
                </div>
            )
        }
    });

});