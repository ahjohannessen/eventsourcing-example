Blog
----

<table>
    <tr>
        <td><b>Part</b></td>
        <td><b>Branch</b></td>
    </tr>
    <tr>
        <td><a href="http://krasserm.blogspot.com/2011/11/building-event-sourced-web-application.html">Building an Event-Sourced Web Application - Part 1: Domain Model, Events and State</a></td>
        <td><a href="https://github.com/krasserm/eventsourcing-example/tree/part-1">part-1</a></td>
    </tr>
    <tr>
        <td>Building an Event-Sourced Web Application - Part 2: ... </td>
        <td><a href="https://github.com/krasserm/eventsourcing-example/tree/part-2">part-2</a></td>
    </tr>
</table>

Run
---

    sbt run-main dev.example.eventsourcing.server.Webserver

Then go to [http://localhost:8080](http://localhost:8080) and create some invoices.

Web API
-------

The example application's RESTful service interface supports HTML, XML and JSON as representation formats. Content negotiation is done via the `Accept` HTTP header. The following examples show how to get different representations of `invoice-3`

### HTML

Enter [http://localhost:8080/invoice/invoice-3](http://localhost:8080/invoice/invoice-3) into your browser. Provided you have created an invoice with id `invoice-3` before you should see something like

![invoice-3](https://github.com/krasserm/eventsourcing-example/raw/master/doc/images/invoice-3.png)

### XML

    curl -H "Accept: application/xml" http://localhost:8080/invoice/invoice-3

yields

    <draft-invoice id="invoice-3" version="2">
        <total>12.8</total>
        <sum>12.8</sum>
        <discount>0</discount>
        <items>
            <item>
                <description>item-1</description>
                <count>1</count>
                <amount>4.1</amount>
            </item>
            <item>
                <description>item-2</description>
                <count>3</count>
                <amount>2.9</amount>
            </item>
        </items>
    </draft-invoice>

### JSON

    curl -H "Accept: application/json" http://localhost:8080/invoice/invoice-3

yields

    {
     "draft-invoice":
     {
      "@id":"invoice-3",
      "@version":"2",
      "total":"12.8",
      "sum":"12.8",
      "discount":"0",
      "items":
      {
       "item":
       [
       {
        "description":"item-1",
        "count":"1",
        "amount":"4.1"
       },
       {
        "description":"item-2",
        "count":"3",
        "amount":"2.9"
       }
       ]
      }
     }
    }
