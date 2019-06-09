package json

import com.gitlab.hopebaron.websocket.entity.Message
import com.gitlab.hopebaron.websocket.entity.Snowflake
import kotlinx.serialization.json.Json
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

private fun file(name: String): String {
    val loader = ChannelTest::class.java.classLoader
    return loader.getResource("json/message/$name.json").readText()
}

class MessageTest : Spek({

    describe("message") {
        it("is deserialized correctly") {
            val message = Json.parse(Message.serializer(), file("message"))

            with(message) {
                reactions!!.size shouldBe 1
                with(reactions!!.first()) {
                    count shouldBe 1
                    me shouldBe false
                    with(emoji) {
                        id shouldBe null
                        name shouldBe "🔥"
                    }
                }
                attachments shouldBe emptyList()
                tts shouldBe false
                embeds shouldBe emptyList()
                timestamp shouldBe "2017-07-11T17:27:07.299000+00:00"
                mentionEveryone shouldBe false
                id shouldBe Snowflake("334385199974967042")
                pinned shouldBe false
                editedTimestamp shouldBe null
                with(author) {
                    username shouldBe "Mason"
                    discriminator shouldBe "9999"
                    id shouldBe Snowflake("53908099506183680")
                    avatar shouldBe "a_bab14f271d565501444b2ca3be944b25"
                }
                mentionRoles shouldBe emptyList()
                content shouldBe "Supa Hot"
                channelId shouldBe Snowflake("290926798999357250")
                mentions shouldBe emptyList()
                type.code shouldBe 0
            }

        }
    }

})