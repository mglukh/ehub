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

/** @jsx React.DOM */
define([
        'react',
        'subscriberMixin',
        'admin/gate/StartStopButton',
        'admin/agent/datatap/ListContainer'
    ],
    function (React,
              subscriberMixin,
              StartStopButton,
              TapListContainer) {

        return React.createClass({
            mixins: [subscriberMixin],

            subscriptionConfig: function () {
                return {route: this.props.id, topic: 'info', target: 'info'};
            },
            getInitialState: function () {
                return {info: null, selected: false}
            },

            renderData: function () {
                return (
                    <div>
                    <a href="#" onClick={this.props.handleSelection(this.props.id)}>{this.state.info.name}</a> {this.state.info_stale ? "Stale" : "not stale"} : {this.state.info.text} :
                        <StartStopButton state={this.state.info.state} route={this.props.id} />
                    </div>
                );
            },
            renderLoading: function () {
                return (
                    <div>loading...</div>
                );
            },

            render: function () {
                if (this.state.info) {
                    return this.renderData();
                } else {
                    return this.renderLoading();
                }
            }
        });

    });