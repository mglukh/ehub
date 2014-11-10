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
define(['react', 'sendOnlyMixin'], function (React, sendOnlyMixin) {

    // use this.sendCommand(subject, data) to talk to server

    return React.createClass({

        mixins: [sendOnlyMixin],

        getInitialState: function () {
            return {}
        },

        handleAdd: function(e) {
            var name = this.refs.name.getDOMNode().value.trim();
            var config = this.refs.config.getDOMNode().value.trim();
            if (!name || !config) return;

            this.sendCommand(this.props.id, "addTap", {name: name, config: config});

            this.refs.name.getDOMNode().value = null;
            this.refs.config.getDOMNode().value = null;

            $('#myModal').modal('hide');
            return true;
        },

        render: function () {

            var cx = React.addons.classSet;
            var buttonClasses = cx({
                'disabled': !this.state.connected
            });

            return (
                <div>
                    <button type="button" className={"btn btn-primary btn-sm " + buttonClasses} data-toggle="modal" data-target="#myModal" >
                    Add new tap
                    </button>

                    <div className="modal fade" id="myModal" tabIndex="-1" role="dialog" aria-labelledby="myModalLabel" aria-hidden="true">
                        <div className="modal-dialog">
                            <div className="modal-content">
                                <div className="modal-header">
                                    <button type="button" className="close" data-dismiss="modal"><span aria-hidden="true">&times;</span><span className="sr-only">Close</span></button>
                                    <h4 className="modal-title" id="myModalLabel">Modal title</h4>
                                </div>
                                <div className="modal-body">
                                    <input type="text" className="form-control" ref="name" placeholder="Tap name"/>
                                    <input type="text" className="form-control" ref="config" placeholder="Tap config"/>
                                </div>
                                <div className="modal-footer">
                                    <button type="button" className="btn btn-default" data-dismiss="modal">Close</button>
                                    <button type="button" className="btn btn-primary" onClick={this.handleAdd}>Add</button>
                                </div>
                            </div>
                        </div>
                    </div>

                </div>
            );
        }
    });

});