package com.monadial.waygrid.common.application.util.logging

import com.monadial.waygrid.common.application.algebra.LoggerContext

import scala.annotation.*
import scala.compiletime.summonInline
import scala.quoted.*

/**
 * Marks a class or object with a log category that will be
 * automatically injected into the implicit LoggerContext.
 *
 * Example:
 * {{{
 * @logCategory("waystation")
 * object WaystationService:
 *   Logger[IO].info("started") // category = "waystation"
 * }}}
 */
class logCategory(val name: String) extends StaticAnnotation:
  inline def apply(defn: Any): Any = ${ LogCategoryMacro.impl('name, 'defn) }

object LogCategoryMacro:
  def impl(nameExpr: Expr[String], defnExpr: Expr[Any])(using Quotes): Expr[Any] =
    import quotes.reflect.*

    def enrichBody(body: Term): Term =
      // Wrap the body in a new given LoggerContext = old.copy(category = Some(name))
      '{
        @nowarn("msg=unused local definition")
        given LoggerContext = summonInline[LoggerContext].copy(category = Some($nameExpr))
        ${ body.asExpr }
      }.asTerm

    defnExpr.asTerm match
      case objDef @ DefDef(_, _, _, Some(body)) =>
        val newBody = enrichBody(body)
        val newDef  = DefDef.copy(objDef)(objDef.name, objDef.paramss, objDef.returnTpt, Some(newBody))
        newDef.asExpr
      case clsDef @ ClassDef(name, ctr, parents, self, body) =>
        val newBody = enrichBody(Block(body, Literal(UnitConstant())))
        val newCls  = ClassDef.copy(clsDef)(name, ctr, parents, self, newBody.asExpr.asTerm :: Nil)
        newCls.asExpr
      case _ =>
        report.error("@logCategory can be applied only to class or object definitions")
        defnExpr
