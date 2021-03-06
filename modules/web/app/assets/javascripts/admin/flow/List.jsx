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

define(['react', 'coreMixin', 'subscriberMixin', 'admin/flow/ListItem'], function (React, coreMixin, subscriberMixin, ListItem) {

    return React.createClass({
        mixins: [coreMixin, subscriberMixin],

        subscriptionConfig: function () {
            return {address: this.props.addr, route:'flows', topic:'list', target: 'list'};
        },
        getInitialState: function () {
            return {data: null}
        },

        renderData: function() {
            var props = this.props;
            return (
                <div>
                {this.state.list.map(function (el) {
                    return <ListItem {...props} key={el.id} id={el.id}/>;
                    })}
                </div>
            );
        },
        renderLoading: function() {
            return (
                <div>loading...</div>
            );
        },

        render: function () {
            if (this.state.list) {
                return this.renderData();
            } else {
                return this.renderLoading();
            }
        }
    });

});