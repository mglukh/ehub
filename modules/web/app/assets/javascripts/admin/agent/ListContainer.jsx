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
define(['react', 'admin/agent/List', 'admin/agent/datatap/ListContainer'],
    function (React, List, TapListContainer) {

    return React.createClass({

        getInitialState: function () {
            return { selection: false }
        },

        handleSelection: function(id) {
            this.setState({selection: id});
        },

        render: function () {

            var selection = this.state.selection
                ? <div><h3>Selected:</h3><TapListContainer id={this.state.selection} /></div>
                : <div>Please select agent</div>

            return (
                <div>
                    <List handleSelection={this.handleSelection} />
                    {selection}
                </div>
            )
        }
    });

});