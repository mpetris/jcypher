/************************************************************************
 * Copyright (c) 2014 IoT-Solutions e.U.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *  http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ************************************************************************/

package test;

import static org.junit.Assert.assertFalse;
import iot.jcypher.JcQuery;
import iot.jcypher.JcQueryResult;
import iot.jcypher.database.DBAccessFactory;
import iot.jcypher.database.DBProperties;
import iot.jcypher.database.DBType;
import iot.jcypher.database.IDBAccess;
import iot.jcypher.query.api.IClause;
import iot.jcypher.query.factories.clause.CREATE;
import iot.jcypher.query.factories.clause.MATCH;
import iot.jcypher.query.factories.clause.RETURN;
import iot.jcypher.query.values.JcNode;
import iot.jcypher.query.values.JcRelation;
import iot.jcypher.util.Util;

import java.util.Properties;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import util.TestDataReader;

//@Ignore
public class DBAccessTest extends AbstractTestSuite {

	private static IDBAccess dbAccess;
	
	@BeforeClass
	public static void before() {
		Properties props = new Properties();
		
		// properties for remote access and for embedded access
		// (not needed for in memory access)
		props.setProperty(DBProperties.SERVER_ROOT_URI, "http://localhost:7474");
		props.setProperty(DBProperties.DATABASE_DIR, "C:/NEO4J_DBS/01");
		
		dbAccess = DBAccessFactory.createDBAccess(DBType.IN_MEMORY, props);
	}
	
	@AfterClass
	public static void after() {
		if (dbAccess != null) {
			dbAccess.close();
			dbAccess = null;
		}
	}
	
	@Test
	public void testDBAccess_01() {
		createDB_01();
		queryDB_01();
		return;
	}
	
	private void createDB_01() {
		
		JcNode matrix1 = new JcNode("matrix1");
		JcNode matrix2 = new JcNode("matrix2");
		JcNode matrix3 = new JcNode("matrix3");
		JcNode keanu = new JcNode("keanu");
		JcNode laurence = new JcNode("laurence");
		JcNode carrieanne = new JcNode("carrieanne");
		
		/*******************************/
		JcQuery query = new JcQuery();
		query.setClauses(new IClause[] {
				CREATE.node(matrix1).label("Movie")
						.property("title").value("The Matrix")
						.property("year").value("1999-03-31"),
				CREATE.node(matrix2).label("Movie")
						.property("title").value("The Matrix Reloaded")
						.property("year").value("2003-05-07"),
				CREATE.node(matrix3).label("Movie")
						.property("title").value("The Matrix Revolutions")
						.property("year").value("2003-10-27"),
				CREATE.node(keanu).label("Actor")
						.property("name").value("Keanu Reeves")
						.property("rating").value(5),
				CREATE.node(laurence).label("Actor")
						.property("name").value("Laurence Fishburne")
						.property("rating").value(6),
				CREATE.node(carrieanne).label("Actor")
						.property("name").value("Carrie-Anne Moss")
						.property("rating").value(7),
				CREATE.node(keanu).relation().out().type("ACTS_IN").property("role").value("Neo").node(matrix1),
				CREATE.node(keanu).relation().out().type("ACTS_IN").property("role").value("Neo").node(matrix2),
				CREATE.node(keanu).relation().out().type("ACTS_IN").property("role").value("Neo").node(matrix3),
				CREATE.node(laurence).relation().out().type("ACTS_IN").property("role").value("Morpheus").node(matrix1),
				CREATE.node(laurence).relation().out().type("ACTS_IN").property("role").value("Morpheus").node(matrix2),
				CREATE.node(laurence).relation().out().type("ACTS_IN").property("role").value("Morpheus").node(matrix3),
				CREATE.node(carrieanne).relation().out().type("ACTS_IN").property("role").value("Trinity").node(matrix1),
				CREATE.node(carrieanne).relation().out().type("ACTS_IN").property("role").value("Trinity").node(matrix2),
				CREATE.node(carrieanne).relation().out().type("ACTS_IN").property("role").value("Trinity").node(matrix3)
		});
		
		JcQueryResult result = dbAccess.execute(query);
		if (result.hasErrors())
			printErrors(result);
		assertFalse(result.hasErrors());
		
		return;
	}
	
	private void queryDB_01() {
		JcQueryResult result;
		String resultString;
		String testId;
		
		setDoPrint(true);
		setDoAssert(true);

		TestDataReader tdr = new TestDataReader("/test/dbaccess/Test_DBACCESS_01.txt");
		
		JcNode movie = new JcNode("movie");
		JcNode n = new JcNode("n");
		JcRelation r = new JcRelation("r");
		
		/*******************************/
		JcQuery query = new JcQuery();
		query.setClauses(new IClause[] {
				MATCH.node(movie).label("Movie").property("title").value("The Matrix"),
				RETURN.value(movie)
		});
		result = dbAccess.execute(query);
		resultString = Util.writePretty(result.getJsonResult());
		print(resultString);
		testId = "ACCESS_01";
		assertQuery(testId, resultString, tdr.getTestData(testId));
		
		/*******************************/
		query = new JcQuery();
		query.setClauses(new IClause[] {
				MATCH.node(n).relation(r).out().node(),
				//RETURN.value(n.property("name"))
				RETURN.ALL()
		});
		result = dbAccess.execute(query);
		resultString = Util.writePretty(result.getJsonResult());
		print(resultString);
		testId = "ACCESS_02";
		assertQuery(testId, resultString, tdr.getTestData(testId));
		
		return;
	}
}
