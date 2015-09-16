(ns open-company-web.data.finances)

(def finances {
  :name "Buffer"
  :currency "USD"
  
  :sections ["oc:finances"]
  
  :oc:finances {
    :data [
      {
        :period "2015-08"
        :cash 1209133
        :revenue 977
        :costs 27155
      }
      {
        :period "2015-07"
        :cash 1235311
        :revenue 512
        :costs 26412
      }
      {
        :period "2015-06"
        :cash 1261376
        :revenue 286
        :costs 26577
      }
      {
        :period "2015-05"
        :cash 1287667
        :revenue 0
        :costs 44960
      }
      {
        :period "2015-04"
        :cash 82627
        :revenue 0
        :costs 27861
      }
      {
        :period "2015-03"
        :cash 109746
        :revenue 0
        :costs 27119
      }
      {
        :period "2015-02"
        :cash 136865
        :revenue 0
        :costs 22345
      }
      {
        :period "2015-01"
        :cash 159210
        :revenue 0
        :costs 20633
      }
      {
        :period "2014-12"
        :cash 179843
        :revenue 0
        :costs 38762
      }
      {
        :period "2014-11"
        :cash 218605
        :revenue 0
        :costs 18814
      }
      {
        :period "2014-10"
        :cash 237419
        :revenue 0
        :costs 19546
      }      
    ]
    :updated-at "2015-09-14T20:49:19Z"
    :author {
      :name "Iacopo Carraro"
      :slack_id "U06STCKLN"
      :image "https://secure.gravatar.com/avatar/98b5456ea1c562024f41501ffd7bc3c6.jpg?s=192&d=https%3A%2F%2Fslack.global.ssl.fastly.net%2F66f9%2Fimg%2Favatars%2Fava_0022.png"
    }
    :commentary {
      :body "<p>...</p>"
      :updated-at "2015-09-14T20:49:19Z"
      :author {
        :name "Stuart Levinson"
        :slack_id "U06SQLDFT"
        :image "https://secure.gravatar.com/avatar/6ef85399c45b7affe7fc8fb361a3366f.jpg?s=192&d=https%3A%2F%2Fslack.global.ssl.fastly.net%2F66f9%2Fimg%2Favatars%2Fava_0015.png"
      }
      :links [
        {
          :rel "self"
          :method "GET"
          :href "/companies/buffer/oc:finances/commentary"
          :type "application/vnd.open-company.commentary.v1+json"
        }
        {
          :rel "update"
          :method "POST"
          :href "/companies/buffer/oc:finances/commentary"
          :type "application/vnd.open-company.commentary.v1+json"
        }
      ]
    }
    :links [
      {
        :rel "self"
        :method "GET"
        :href "/companies/buffer/oc:finances"
        :type "application/vnd.open-company.section.oc:finances.v1+json"
      }
      {
        :rel "update"
        :method "POST"
        :href "/companies/buffer/oc:finances"
        :type "application/vnd.open-company.section.oc:finances.v1+json"
      }
      {
        :rel "partial-update"
        :method "PATCH"
        :href "/companies/buffer/oc:finances"
        :type "application/vnd.open-company.section.oc:finances.v1+json"
      }
    ]
  }
  
  :revisions [
    {
      :rel "revision"
      :method "GET"
      :href "/companies/buffer?as-of=2015-09-11T22:14:24Z"
      :type "application/vnd.open-company.v1+json"
      :updated-at "2015-09-11T22:14:24Z"
      :author {
        :name "Iacopo Carraro"
        :slack_id "U06STCKLN"
        :image "https://secure.gravatar.com/avatar/98b5456ea1c562024f41501ffd7bc3c6.jpg?s=192&d=https%3A%2F%2Fslack.global.ssl.fastly.net%2F66f9%2Fimg%2Favatars%2Fava_0022.png"
      }
    }
    {
      :rel "revision"
      :method "GET"
      :href "/companies/buffer?as-of=2015-09-10T22:14:24Z"
      :type "application/vnd.open-company.v1+json"
      :updated-at "2015-09-10T22:14:24Z"
      :author {
        :name "Stuart Levinson"
        :slack_id "U06SQLDFT"
        :image "https://secure.gravatar.com/avatar/6ef85399c45b7affe7fc8fb361a3366f.jpg?s=192&d=https%3A%2F%2Fslack.global.ssl.fastly.net%2F66f9%2Fimg%2Favatars%2Fava_0015.png"
      }
    }
    {
      :rel "revision"
      :method "GET"
      :href "/companies/buffer?as-of=2015-09-09T22:14:24Z"
      :type "application/vnd.open-company.v1+json"
      :updated-at "2015-09-09T22:14:24Z"
      :author {
        :name "Stuart Levinson"
        :slack_id "U06SQLDFT"
        :image "https://secure.gravatar.com/avatar/6ef85399c45b7affe7fc8fb361a3366f.jpg?s=192&d=https%3A%2F%2Fslack.global.ssl.fastly.net%2F66f9%2Fimg%2Favatars%2Fava_0015.png"
      }
    }
    {
      :rel "revision"
      :method "GET"
      :href "/companies/buffer?as-of=2015-09-08T22:14:24Z"
      :type "application/vnd.open-company.v1+json"
      :updated-at "2015-09-08T22:14:24Z"
      :author {
        :name "Stuart Levinson"
        :slack_id "U06SQLDFT"
        :image "https://secure.gravatar.com/avatar/6ef85399c45b7affe7fc8fb361a3366f.jpg?s=192&d=https%3A%2F%2Fslack.global.ssl.fastly.net%2F66f9%2Fimg%2Favatars%2Fava_0015.png"
      }
    }
    {
      :rel "revision"
      :method "GET"
      :href "/companies/buffer?as-of=2015-09-07T22:14:24Z"
      :type "application/vnd.open-company.v1+json"
      :updated-at "2015-09-07T22:14:24Z"
      :author {
        :name "Stuart Levinson"
        :slack_id "U06SQLDFT"
        :image "https://secure.gravatar.com/avatar/6ef85399c45b7affe7fc8fb361a3366f.jpg?s=192&d=https%3A%2F%2Fslack.global.ssl.fastly.net%2F66f9%2Fimg%2Favatars%2Fava_0015.png"
      }
    }
  ]

  :links [
    {
      :rel "self"
      :method "GET"
      :href "/companies/buffer"
      :type "application/vnd.open-company.v1+json"
    }
    {
      :rel "update"
      :method "PUT"
      :href "/companies/buffer"
      :type "application/vnd.open-company.v1+json"
    }
    {
      :rel "partial-update"
      :method "PATCH"
      :href "/companies/buffer"
      :type "application/vnd.open-company.v1+json"
    }
    {
      :rel "delete"
      :method "DELETE"
      :href "/companies/buffer"
    }
  ]
})