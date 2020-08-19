package convex.core.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import convex.core.lang.impl.RecordFormat;

public class RecordTest {

	public static void doRecordTests(ARecord r) {
		
		RecordFormat format=r.getFormat();

		AVector<Keyword> keys=format.getKeys();
		int n=(int) keys.count();
		
		AVector<Object> vals=r.getValues();
		assertEquals(n,vals.size());
		
		Object[] vs=new Object[n]; // new array to extract values
		for (int i=0; i<n; i++) {
			// standard element access by key
			Keyword k=keys.get(i);
			Object v=r.get(k);
			vs[i]=v;
			
			// entry based access by key
			MapEntry<Keyword,Object> me0=r.getEntry(k);
			assertEquals(k,me0.getKey());
			assertEquals(v,me0.getValue());
			
			// TODO: consider this invariant?
			assertEquals(r.toHashMap(),r.assoc(k, v));
			
			// indexed access
			assertEquals(v,vals.get(i));
			
			// indexed entry-wise access
			MapEntry<Keyword,Object> me=r.entryAt(i);
			assertEquals(k,me.getKey());
			assertEquals(v,me.getValue());
		}
		
		assertSame(r,r.updateAll(r.getValuesArray()));
		
		CollectionsTest.doDataStructureTests(r);
	}
}
