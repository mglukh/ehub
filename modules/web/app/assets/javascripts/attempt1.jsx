/** @jsx React.DOM */
require(['react','list'], function (React, list) {

    //alert("loaded?" + React + " : " + Timer );

    //React.renderComponent(<Timer start={new Date()}/>, document.getElementById('content'));

    React.renderComponent(<list />, document.getElementById('content'));

});