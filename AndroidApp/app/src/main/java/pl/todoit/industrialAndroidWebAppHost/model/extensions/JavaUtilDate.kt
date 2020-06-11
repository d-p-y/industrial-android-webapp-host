package pl.todoit.industrialAndroidWebAppHost.model.extensions

import java.util.*

fun Date.diffInMilisecTo(scnd: Date) : Long = Math.abs(scnd.getTime() - this.getTime())
