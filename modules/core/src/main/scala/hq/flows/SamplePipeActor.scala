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

package hq.flows

import akka.actor.Props
import akka.stream.FlowMaterializer
import common.actors.{ActorObjWithoutConfig, ActorWithComposableBehavior}
import hq.flows.core.Builder
import play.api.libs.json.Json

import scalaz.{-\/, \/-}

object SamplePipeActor extends ActorObjWithoutConfig {
  def props: Props = Props(new SamplePipeActor())

  override def id: String = "pipe"
}

class SamplePipeActor extends ActorWithComposableBehavior {


  val myTestFlow =
    """{
      |	"id": "flow1",
      |
      |	"tap": {
      |		"class": "gate",
      |		"props": {
      |			"name": "aaa"
      |		}
      |	},
      |
      |	"pipeline": [
      |		{
      |			"class": "log",
      |			"props": {
      |				"level": "INFO",
      |				"logger": "flow1-before"
      |			}
      |		},
      |		{
      |			"class": "enrich",
      |			"props": {
      |				"fields": [
      |					{
      |						"name": "source.idAsNum.${source.id}",
      |						"value": "${id}",
      |						"type": "n"
      |					},
      |					{
      |						"name": "source.idAsString-${source.id}",
      |						"value": "${id}",
      |						"type": "s"
      |					},
      |					{
      |						"name": "source.v3",
      |						"value": "name1=value1, name2=98765, name3=3",
      |						"type": "s"
      |					}
      |				],
      |				"tags": ["tag1","tag2","tag2"]
      |			}
      |		},
      |		{
      |			"class": "grok",
      |			"props": {
      |				"source": "source/v3",
      |				"pattern": "([\\w\\d_]+)=([^\\s,]*)",
      |				"groups": "g1,g2",
      |				"fields": "extract/${g1},extract/${g1}_1,extract/${g1}_2",
      |				"values": "${g2},${g1},${g1}_2",
      |				"types": "n,s,s"
      |			},
      |			"condition": {
      |				"class": "any",
      |				"list": [
      |					{
      |						"class": "tag",
      |						"props": {
      |							"name": "field_name",
      |							"is": "blabla"
      |							}
      |					},
      |					{
      |						"class": "field",
      |						"props": {
      |							"name": "value",
      |							"is": "New output"
      |							}
      |					}
      |				]
      |			}
      |		},
      |		{
      |			"class": "grok",
      |			"props": {
      |				"source": "value",
      |				"pattern": "(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3})",
      |				"groups": "g1",
      |				"fields": "extract/date",
      |				"values": "${g1}",
      |				"types": "s"
      |			},
      |			"condition": {
      |				"class": "all",
      |				"list": [
      |					{
      |						"class": "tag",
      |						"props": {
      |							"name": "tag1",
      |							"is": "tag1"
      |							}
      |					},
      |					{
      |						"class": "field",
      |						"props": {
      |							"name": "value",
      |							"is": "New output"
      |							}
      |					}
      |				]
      |			}
      |		},
      |		{
      |			"class": "log",
      |			"props": {
      |				"level": "WARN",
      |				"logger": "flow1-after"
      |			}
      |		},
      |		{
      |			"class": "log",
      |			"props": {
      |				"level": "WARN",
      |				"logger": "flow1-m1"
      |			},
      |			"condition": {
      |				"class": "all",
      |				"list": [
      |					{
      |						"class": "tag",
      |						"props": {
      |							"name": "tag1",
      |							"is": "tag1"
      |							}
      |					}
      |				]
      |			}
      |		},
      |		{
      |			"class": "log",
      |			"props": {
      |				"level": "WARN",
      |				"logger": "flow1-m2"
      |			},
      |			"condition": {
      |				"class": "all",
      |				"list": [
      |					{
      |						"class": "tag",
      |						"props": {
      |							"name": "tag1",
      |							"isnot": "tag1"
      |							}
      |					}
      |				]
      |			}
      |		},
      |		{
      |			"class": "log",
      |			"props": {
      |				"level": "WARN",
      |				"logger": "flow1-m2"
      |			},
      |			"condition": {
      |				"class": "all",
      |				"list": [
      |					{
      |						"class": "field",
      |						"props": {
      |							"name": "value",
      |							"is": "New output"
      |							}
      |					}
      |				]
      |			}
      |		}
      |
      |	],
      |
      |	"sink": {
      |		"class": "gate",
      |		"props": {
      |			"name": "bbb"
      |		}
      |	}
      |
      |
      |}""".stripMargin

  val flow2 =
    """{
      |	"id": "flow2",
      |
      |	"tap": {
      |		"class": "gate",
      |		"props": {
      |			"name": "bbb"
      |		}
      |	},
      |	"pipeline": [
      |		{
      |			"class": "log",
      |			"props": {
      |				"level": "INFO",
      |				"logger": "flow2-ok"
      |			}
      |		}
      |	],
      |	"sink": {
      |		"class": "blackhole",
      |		"props": {}
      |	}
      |
      |
      |}""".stripMargin

  val pipe_tcp_6010 =
    """{
      |	"id": "tcp_6010",
      |	"type": "active",
      |	"input": {
      |		"class":"tcp",
      |		"props": {
      |			"name": "mainport",
      |			"bind": {
      |				"port":6010,
      |				"host":"localhost"
      |			}
      |		}
      |	},
      |	"pipeline": [
      |		{
      |			"instruction": "enrich",
      |			"props": {
      |				"fields": {
      |					"source": "mainsource"
      |				},
      |				"tags": ["tcp","chunk"]
      |			}
      |		},
      |		{
      |			"instruction": "fork",
      |			"props": {
      |				"pipeline": [
      |					{
      |						"instruction": "log",
      |						"props": {
      |							"level": "INFO",
      |							"logger": "mainport"
      |						}
      |					}
      |				]
      |			}
      |		},
      |		{
      |			"instruction": "gate",
      |			"props": {
      |				"name": "testgate"
      |			}
      |		},
      |		{
      |			"instruction": "gate",
      |			"props": {
      |				"name": "sample_reconciliator"
      |			}
      |		}
      |
      |	]
      |}
      | """.stripMargin
  val pipe_second_gate =
    """{
      |	"id": "second_gate",
      |	"type": "active",
      |	"input": {
      |		"class":"gate",
      |		"props": {
      |			"name": "second_gate"
      |		}
      |	},
      |	"pipeline": [
      |		{
      |			"instruction": "grok",
      |			"props": {
      |       "source": "source/v3",
      |				"pattern": "([\\w\\d_]+)=([^\\s,]*)",
      |       "groups": "g1,g2",
      |       "fields": "extract/${g1},extract/${g1}_1,extract/${g1}_2",
      |       "values": "${g2},${g1},${g1}_2",
      |       "types": "n,s,s"
      |			}
      |		},
      |
      |		{
      |			"instruction": "log",
      |			"props": {
      |				"level": "INFO",
      |				"logger": "second_gate"
      |			}
      |		}
      | ]
      |}""".stripMargin
  val pipe_sample_testgate =
    """{
      |	"id": "testgate",
      |	"type": "active",
      |	"input": {
      |		"class":"gate",
      |		"props": {
      |			"name": "aaa"
      |		}
      |	},
      |	"pipeline": [
      |		{
      |			"instruction": "log",
      |			"props": {
      |				"level": "INFO",
      |				"logger": "testgate"
      |			}
      |		},
      |		{
      |			"instruction": "drop",
      |			"condition": {
      |				"any": [
      |					  {
      |						"tag": {
      |             "name": "chunk",
      |							"is":	".+"
      |						}},
      |						{"field": {
      |							"name": "source",
      |							"is":	"todrop"
      |						}
      |       	}
      |				]
      |     }
      |		},
      |		{
      |			"instruction": "enrich",
      |			"props": {
      |				"fields": [
      |         {
      |					  "name": "source.bla2.${source.v1}_${source/v2}",
      |					  "value": "${source.v1}",
      |					  "type": "n"
      |				  },
      |         {
      |					  "name": "source.bla3.${source.v1}_${source/v2}",
      |					  "value": "${source.v2}",
      |					  "type": "s"
      |				  }
      |      ],
      |      "tags": ["tag1","tag2","tag2"]
      |			}
      |		},
      |		{
      |			"instruction": "log",
      |			"props": {
      |				"level": "WARN",
      |				"logger": "warnings"
      |			}
      |		},
      |		{
      |			"instruction": "gate",
      |			"props": {
      |				"name": "second_gate"
      |			}
      |		}
      |	]
      |}""".stripMargin
  val pipe_reconciliator =
    """{
      |	"id": "sample_reconciliator",
      |	"type": "active",
      |	"input": {
      |		"class":"gate",
      |		"props": {
      |			"name": "sample_reconciliator",
      |		}
      |	},
      |	"pipeline": [
      |		{
      |			"instruction": "drop",
      |			"conditions": [
      |				{
      |					"or": [
      |						{
      |							"tag": {
      |								"not":	"chunk"
      |							}
      |						}
      |					]
      |				}
      |			],
      |		},
      |		{
      |			"instruction": "reconcile",
      |			"conditions": [
      |				{
      |					"all": [
      |						{
      |							"field": {
      |								"name": "source",
      |								"is":	"mainsource"
      |							}
      |						}
      |					]
      |				}
      |			],
      |			"props": {
      |				"start": {
      |					"class": "regex",
      |					"props": {
      |						"pattern": "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}",
      |						"inclusive": true
      |					}
      |				},
      |				"finish": {
      |					"class": "regex",
      |					"props": {
      |						"pattern": "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}",
      |						"inclusive": false
      |					}
      |				},
      |			}
      |		},
      |		{
      |			"instruction": "grok",
      |			"props": {
      |				"source": "_raw",
      |				"mode": "update",
      |				"pattern": "^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3})",
      |				"vars": "timestamp_str",
      |				"types": "s",
      |				"drop_on_match": false,
      |			}
      |		},
      |		{
      |			"instruction": "enrich_date",
      |			"props": {
      |				"source": "${timestamp_str}",
      |			}
      |		},
      |		{
      |			"instruction": "enrich",
      |			"props": {
      |				"fields": {
      |					"ext.key": "${timestamp_str}_${raw_hash}",
      |					"ext.reconciled_id_list": "${reconciled_id_list}"
      |				}
      |			}
      |		},
      |		{
      |			"instruction": "es",
      |			"props": {
      |				"location": {
      |					"host": "localhost",
      |					"port": 6007,
      |				},
      |				"index": {
      |					"name": "events-$yyyy.$mm.$dd"
      |				},
      |				"document_id": "${ext.key}",
      |				"bulk": 100,
      |				"bulk_flush_sec": 1
      |			}
      |		}
      |	]
      |}
      | """.stripMargin

  implicit val dispatcher = context.system.dispatcher


  //  val pipelinePick = (__ \ 'pipeline).json.pick[JsArray]
  //
  //  val propsFirstPick = ((__)(0) \ 'props).json.pick
  //  val propsPick = (__ \ 'props).json.pick

  override def commonBehavior: Receive = super.commonBehavior

  override def preStart(): Unit = {
    super.preStart()

    implicit val pipe = Json.parse(myTestFlow)
    var x = Builder()(pipe,context)

    x match {
      case -\/(fail) =>
        logger.info(s"Failed with $fail")
      case \/-(flow) =>
        logger.info(s"Successfully built $flow")

        implicit val mat = FlowMaterializer()
        implicit val dispatcher = context.system.dispatcher

        flow.run()

        logger.info(s"Started flow!")
    }

    implicit val pipe2 = Json.parse(flow2)
    var x2 = Builder()(pipe2,context)

    x2 match {
      case -\/(fail) =>
        logger.info(s"flow 2 Failed with $fail")
      case \/-(flow) =>
        logger.info(s"Successfully built flow2 $flow")

        implicit val mat = FlowMaterializer()
        implicit val dispatcher = context.system.dispatcher

        flow.run()

        logger.info(s"Started flow2!")
    }



  }

}
