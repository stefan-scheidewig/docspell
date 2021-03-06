module Comp.CustomFieldTable exposing
    ( Action(..)
    , Model
    , Msg
    , init
    , update
    , view
    , view2
    )

import Api.Model.CustomField exposing (CustomField)
import Comp.Basic as B
import Html exposing (..)
import Html.Attributes exposing (..)
import Html.Events exposing (onClick)
import Styles as S
import Util.Time


type alias Model =
    {}


type Msg
    = EditItem CustomField


type Action
    = NoAction
    | EditAction CustomField


init : Model
init =
    {}


update : Msg -> Model -> ( Model, Action )
update msg model =
    case msg of
        EditItem item ->
            ( model, EditAction item )



--- View


view : Model -> List CustomField -> Html Msg
view _ items =
    div []
        [ table [ class "ui very basic aligned table" ]
            [ thead []
                [ tr []
                    [ th [ class "collapsing" ] []
                    , th [] [ text "Name/Label" ]
                    , th [] [ text "Format" ]
                    , th [] [ text "#Usage" ]
                    , th [] [ text "Created" ]
                    ]
                ]
            , tbody []
                (List.map viewItem items)
            ]
        ]


viewItem : CustomField -> Html Msg
viewItem item =
    tr []
        [ td [ class "collapsing" ]
            [ a
                [ href "#"
                , class "ui basic small blue label"
                , onClick (EditItem item)
                ]
                [ i [ class "edit icon" ] []
                , text "Edit"
                ]
            ]
        , td []
            [ text <| Maybe.withDefault item.name item.label
            ]
        , td []
            [ text item.ftype
            ]
        , td []
            [ String.fromInt item.usages
                |> text
            ]
        , td []
            [ Util.Time.formatDateShort item.created
                |> text
            ]
        ]



--- View2


view2 : Model -> List CustomField -> Html Msg
view2 _ items =
    div []
        [ table [ class S.tableMain ]
            [ thead []
                [ tr []
                    [ th [] []
                    , th [ class "text-left" ] [ text "Name/Label" ]
                    , th [ class "text-left" ] [ text "Format" ]
                    , th [ class "text-center hidden sm:table-cell" ] [ text "#Usage" ]
                    , th [ class "text-center hidden sm:table-cell" ] [ text "Created" ]
                    ]
                ]
            , tbody []
                (List.map viewItem2 items)
            ]
        ]


viewItem2 : CustomField -> Html Msg
viewItem2 item =
    tr [ class S.tableRow ]
        [ B.editLinkTableCell (EditItem item)
        , td [ class "text-left py-4 md:py-2 pr-2" ]
            [ text <| Maybe.withDefault item.name item.label
            ]
        , td [ class "text-left py-4 md:py-2 pr-2" ]
            [ text item.ftype
            ]
        , td [ class "text-center py-4 md:py-2 sm:pr-2 hidden sm:table-cell" ]
            [ String.fromInt item.usages
                |> text
            ]
        , td [ class "text-center py-4 md:py-2 hidden sm:table-cell" ]
            [ Util.Time.formatDateShort item.created
                |> text
            ]
        ]
