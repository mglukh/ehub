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

import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._

import play.api.test._
import play.api.test.Helpers._
import java.io.File

/**
* Add your spec here.
* You can mock out a whole application including requests, plugins etc.
* For more information, consult the wiki.
*/
@RunWith(classOf[JUnitRunner])
class ApplicationSpec extends Specification {

	val modulePath = new File("./modules/common/")
	
	"Common Module" should {

		"send 404 on a bad request" in {
			running(FakeApplication(path = modulePath)) {
				route(FakeRequest(GET, "/boum")) must beNone        
			}
		}
    
		"render the status page" in {
			running(FakeApplication(path = modulePath)) {
				val home = route(FakeRequest(GET, "/status")).get
        
				status(home) must equalTo(OK)
				contentAsString(home) must contain ("Everything is great")
			}
		}
	}
}