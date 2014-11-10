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
define(['react'], function(React) {
    /** @jsx React.DOM */
    /**
     * <TimeMessage elapsed={100} />
     */
    var TimeMessage = React.createClass({
        render: function() {
            var elapsed = Math.round(this.props.elapsed  / 100);
            var seconds = elapsed / 10 + (elapsed % 10 ? '' : '.0' );
            var message =
                'React has been successfully running for ' + seconds + ' seconds.';

            // JSX code
            return <p>{message}</p>;
        }
    });

    /**
     * <Timer start={aDate} />
     */
    var Timer = React.createClass({
        getInitialState: function() {
            return {now: new Date()};
        },

        componentDidMount: function(el, root) {
            var that = this;
            setInterval(function() {
                that.setState({now: new Date()});
            }, 50);
        },

        render: function() {
            // JSX code
            var elapsed = this.state.now.getTime() - this.props.start.getTime();
            return <TimeMessage elapsed={elapsed} />;
        }
    });

    return Timer;
});