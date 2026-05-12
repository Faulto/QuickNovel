package com.lagradost.quicknovel.ui.result

import android.content.Context
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.AttrRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isGone
import com.lagradost.quicknovel.ChapterData
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.databinding.SimpleChapterBinding
import com.lagradost.quicknovel.ui.BaseDiffCallback
import com.lagradost.quicknovel.ui.NoStateAdapter
import com.lagradost.quicknovel.ui.ViewHolderState
import com.lagradost.quicknovel.ui.newSharedPool

class ChapterAdapter(val viewModel: ResultViewModel) :
    NoStateAdapter<ChapterData>(
        diffCallback = BaseDiffCallback(
            itemSame = { a, b -> a.url == b.url },
            contentSame = { a, b -> a == b }
        )) {
    private var zebraEvenColor: Int? = null
    private var zebraOddColor: Int? = null

    companion object {
        val sharedPool =
            newSharedPool {
                setMaxRecycledViews(CONTENT, 10)
            }
    }

    private fun Context.resolveThemeColor(@AttrRes attr: Int): Int {
        val value = TypedValue()
        theme.resolveAttribute(attr, value, true)
        return if (value.resourceId != 0) {
            ContextCompat.getColor(this, value.resourceId)
        } else {
            value.data
        }
    }

    private fun rowColor(context: Context, position: Int): Int {
        val even = zebraEvenColor
        val odd = zebraOddColor
        if (even != null && odd != null) {
            return if (position % 2 == 0) even else odd
        }

        val base = context.resolveThemeColor(R.attr.primaryBlackBackground)
        val newEven = ColorUtils.setAlphaComponent(base, 26)
        val newOdd = ColorUtils.setAlphaComponent(base, 44)
        zebraEvenColor = newEven
        zebraOddColor = newOdd
        return if (position % 2 == 0) newEven else newOdd
    }

    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Any> {
        return ViewHolderState(
            SimpleChapterBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    private fun refresh(
        binding: SimpleChapterBinding,
        card: ChapterData,
        viewModel: ResultViewModel
    ) {
        val alpha = if (viewModel.hasReadChapter(chapter = card)) 0.5F else 1.0F

        binding.name.alpha = alpha
        binding.releaseDate.alpha = alpha
    }

    override fun onBindContent(holder: ViewHolderState<Any>, item: ChapterData, position: Int) {
        val binding = holder.view as? SimpleChapterBinding ?: return
        binding.apply {
            root.setCardBackgroundColor(rowColor(root.context, position))
            name.text = item.name
            releaseDate.text = item.dateOfRelease
            releaseDate.isGone = item.dateOfRelease.isNullOrBlank()
            root.setOnClickListener {
                viewModel.streamRead(item)
                viewModel.isResume = true//to update read status
            }
            root.setOnLongClickListener {
                viewModel.setReadChapter(chapter = item, !viewModel.hasReadChapter(item))
                refresh(binding, item, viewModel)
                return@setOnLongClickListener true
            }
            refresh(binding, item, viewModel)
        }
    }
}
