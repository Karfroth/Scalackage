package com.karfroth.scalackage.converter

import com.karfroth.scalackage.DataTypes

trait Converter {
  def toBuildToolStx(data: DataTypes): String
}

object Converter {
    def toBuildToolStx(data: DataTypes)(implicit converter: Converter): String = converter.toBuildToolStx(data)
}