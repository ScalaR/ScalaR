/*
 * Copyright (c) 2009-2010 European Molecular Biology Laboratory
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package uk.ac.ebi.sr
package functions

import model.{RObject, Sequential}
import collection.mutable.ArrayBuffer
import model.RVal.{RChar, RDouble, RInt, RBool}
import interpreter.{NULL, EmptyIndex}

/**
 * Object used to subset given sequences.
 *
 * Date: Jul 26, 2010
 * @author Taalai Djumabaev
 */
object OneDimensionalSubset {
  import Subset.isOfOneSign

  def `[`[B](seq: Sequential[B], index: RObject)(implicit m: Manifest[B]) = index match {
    case EmptyIndex => seq
    case b: RBool =>
      if (b.length > seq.length) error("subscript out of bounds")
      seq.applyF(subsetLogical(seq, b))
    case i: RInt =>
      //TODO bounds should be checked by values
      isOfOneSign(i) match {
        case (true, false) => seq.applyF(subset(seq, i))
        case (false, true) => seq.applyF(subsetNegation(seq, i))
        case (true, _) => NULL // todo R retruns integer(0)
        case _ => error("only 0's may be mixed with negative subscripts")
      }
    case i: RDouble =>
      //TODO bounds should be checked by values
      val asInt = AsInteger(i)
      isOfOneSign(asInt) match {
        case (true, false) => seq.applyF(subset(seq, asInt))
        case (false, true) => seq.applyF(subsetNegation(seq, asInt))
        case (true, true) => NULL // todo R retruns integer(0)
        case (false, false) => error("only 0's may be mixed with negative subscripts")
      }
    case i: RChar => NULL // todo takes by names attribute
    case o => error("invalid subscript type: " + o.`type`)
  }

  /**
   * if the subsetting vector is of type integer and every number is positive (can be zero)
   */
  private def subset[B](b: Sequential[B], ind: RInt)(implicit m: Manifest[B]) = {
    val a = b.s
    val index = ind.s
    val indLen = index.length
    val len = a.length
    val buf = new ArrayBuffer[B](indLen) //zero indexes can be wastefull here
    var i = 0
    while (i < indLen) {
      val in = index(i)
      if (in == 0) {} else if (in > len || in == ind.NA) buf += b.NA   // in > len since index starts with 1
      else buf += a(in - 1) //since index in R starts with 1
      i += 1
    }
    buf.toArray
  }

  /**
   * if the subsetting vector is of type integer and every number is negative (can be zero)
   */
  private def subsetNegation[B](b: Sequential[B], ind: RInt)(implicit m: Manifest[B]) = {
    val a = b.s
    val len = a.length
    val index = ind.applyChange(java.util.Arrays.sort).s

    val buf = new ArrayBuffer[B]()
    var i = 0
    var j = index.length - 1
    while (index(j) == 0) j -=1      //ignore zeros
    while (i < len) {
      val negated = if (j < 0) -1 else -(index(j) + 1)  //since index in R starts with 1
      if (negated == i) j -=1
      else buf += a(i) //no need to check for NA, since index array contains only negative integers and cannot be NA
      i += 1
    }
    buf.toArray
  }

  /**
   * if the subsetting vector is of type boolean
   */
  private def subsetLogical[B](b: Sequential[B], ind: RBool)(implicit m: Manifest[B]) = {
    val a = b.s
    val len = a.length
    val index = ind.s
    val indLen = index.length //todo warning if index is shorter and not a multiple of len

    val count = math.max(len, indLen)
    val buf = new ArrayBuffer[B](count)
    var i = 0
    var j = 0
    while(i < count) {
      index(j) match {
        case 0 => if (i >= len) buf += b.NA
        case 1 => if (i >= len) buf += b.NA else buf += a(i)
        case ind.NA => buf += b.NA
        case a => error("internal error: undefined value for boolean " + a);
      }
      if (j >= indLen - 1) j = 0 else j += 1
      i += 1
    }
    buf.toArray
  }
}