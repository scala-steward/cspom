package cspom.variable
import org.junit.Assert._
import org.junit.Test
final class BooleanTest {

  @Test
  def testValueOf() {
    assertSame(TrueDomain, BooleanDomain.valueOf(true));
    assertSame(FalseDomain, BooleanDomain.valueOf(false));
  }

  @Test
  def testIsConstant() {
    assertTrue(TrueDomain.isConstant);
    assertTrue(FalseDomain.isConstant);
    assertFalse(BooleanDomain.isConstant);
  }

  @Test
  def testToString() {
    assertEquals("true", TrueDomain.toString());
    assertEquals("false", FalseDomain.toString());
  }

  @Test
  def testGetBoolean() {
    assertSame(TrueDomain.getBoolean, true);
    assertSame(FalseDomain.getBoolean, false);
  }

  @Test(expected = classOf[UnsupportedOperationException])
  def testGetBooleanDomain() {
    BooleanDomain.getBoolean;
  }

  @Test
  def testGetValues() {
    assertEquals(BooleanDomain.values, List(false, true));
  }

}