/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
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
package com.waz.zclient.messages.parts

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.LinearLayout
import com.waz.ZLog.ImplicitTag._
import com.waz.api.Message
import com.waz.model.MessageContent
import com.waz.service.ZMessaging
import com.waz.service.messages.MessageAndLikes
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.messages.UsersController.DisplayName.{Me, Other}
import com.waz.zclient.messages._
import com.waz.zclient.paintcode.ConversationIcon
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.RichView
import com.waz.zclient.{R, ViewHelper}

class MemberChangePartView(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with MessageViewPart with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override val tpe = MsgPart.MemberChange

  setOrientation(LinearLayout.VERTICAL)

  inflate(R.layout.message_member_change_content)

  val zMessaging = inject[Signal[ZMessaging]]
  val users      = inject[UsersController]

  val messageView: SystemMessageView  = findById(R.id.smv_header)
  val position = Signal[Int]()

  val iconGlyph: Signal[Either[Int, Drawable]] = message map { msg =>
    msg.msgType match {
      case Message.Type.MEMBER_JOIN if msg.firstMessage => Right(ConversationIcon(R.color.background_graphite))
      case Message.Type.MEMBER_JOIN =>                     Left(R.string.glyph__plus)
      case _ =>                                            Left(R.string.glyph__minus)
    }
  }


  override def set(msg: MessageAndLikes, part: Option[MessageContent], opts: Option[MsgBindOptions]) = {
    super.set(msg, part, opts)
    opts.foreach(position ! _.position)

  }

  val memberNames = users.memberDisplayNames(message, boldNames = true)

  val senderName = message.map(_.userId).flatMap(users.displayName)

  val linkText = for {
    zms         <- zMessaging
    msg         <- message
    displayName <- senderName
    members     <- memberNames
  } yield {
    import Message.Type._
    val me = zms.selfUserId
    val userId = msg.userId

    (msg.msgType, displayName, msg.members.toSeq) match {
      case (MEMBER_JOIN, Me|Other(_), _)         if msg.firstMessage && msg.name.isDefined => getString(R.string.content__system__with_participant, members)
      case (MEMBER_JOIN, Me, _)                  if msg.firstMessage                       => getString(R.string.content__system__you_started_participant, "", members)
      case (MEMBER_JOIN, Other(name), Seq(`me`)) if msg.firstMessage                       => getString(R.string.content__system__other_started_you, name)
      case (MEMBER_JOIN, Other(name), _)         if msg.firstMessage                       => getString(R.string.content__system__other_started_participant, name, members)
      case (MEMBER_JOIN, Me, Seq(`me`)) if userId == me                                    => getString(R.string.content__system__you_joined).toUpperCase
      case (MEMBER_JOIN, Me, _)                                                            => getString(R.string.content__system__you_added_participant, "", members).toUpperCase
      case (MEMBER_JOIN, Other(name), Seq(`me`))                                           => getString(R.string.content__system__other_added_you, name).toUpperCase
      case (MEMBER_JOIN, Other(name), Seq(`userId`))                                       => getString(R.string.content__system__other_joined, name).toUpperCase
      case (MEMBER_JOIN, Other(name), _)                                                   => getString(R.string.content__system__other_added_participant, name, members).toUpperCase
      case (MEMBER_LEAVE, Me, Seq(`me`))                                                   => getString(R.string.content__system__you_left).toUpperCase
      case (MEMBER_LEAVE, Me, _)                                                           => getString(R.string.content__system__you_removed_other, "", members).toUpperCase
      case (MEMBER_LEAVE, Other(name), Seq(`me`))                                          => getString(R.string.content__system__other_removed_you, name).toUpperCase
      case (MEMBER_LEAVE, Other(name), Seq(`userId`))                                      => getString(R.string.content__system__other_left, name).toUpperCase
      case (MEMBER_LEAVE, Other(name), _)                                                  => getString(R.string.content__system__other_removed_other, name, members).toUpperCase
    }
  }

  message.map(m => if (m.firstMessage && m.name.nonEmpty) Some(16) else None)
    .map(_.map(toPx))
    .onUi(_.foreach(this.setMarginTop))

  iconGlyph {
    case Left(i) => messageView.setIconGlyph(i)
    case Right(d) => messageView.setIcon(d)
  }

  linkText.on(Threading.Ui) { messageView.setText }

}
