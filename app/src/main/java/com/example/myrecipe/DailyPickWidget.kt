package com.example.myrecipe

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.myrecipe.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DailyPickWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_chef_pick)

            // Intent to launch app when clicking widget
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_recipe_title, pendingIntent)

            CoroutineScope(Dispatchers.IO).launch {
                val db = AppDatabase.getDatabase(context)
                val recipe = db.recipeDao().getAllRecipes().randomOrNull()
                
                withContext(Dispatchers.Main) {
                    if (recipe != null) {
                        views.setTextViewText(R.id.widget_recipe_title, recipe.title)
                        views.setTextViewText(R.id.widget_recipe_desc, recipe.description ?: "Ready to cook?")
                    } else {
                        views.setTextViewText(R.id.widget_recipe_title, "No Recipes Found")
                        views.setTextViewText(R.id.widget_recipe_desc, "Add some recipes to ChefMate!")
                    }
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }
        }
    }
}
