module Page.ManageData.Update exposing (update)

import Comp.EquipmentManage
import Comp.OrgManage
import Comp.PersonManage
import Comp.SpaceManage
import Comp.TagManage
import Data.Flags exposing (Flags)
import Page.ManageData.Data exposing (..)


update : Flags -> Msg -> Model -> ( Model, Cmd Msg )
update flags msg model =
    case msg of
        SetTab t ->
            let
                m =
                    { model | currentTab = Just t }
            in
            case t of
                TagTab ->
                    update flags (TagManageMsg Comp.TagManage.LoadTags) m

                EquipTab ->
                    update flags (EquipManageMsg Comp.EquipmentManage.LoadEquipments) m

                OrgTab ->
                    update flags (OrgManageMsg Comp.OrgManage.LoadOrgs) m

                PersonTab ->
                    update flags (PersonManageMsg Comp.PersonManage.LoadPersons) m

                SpaceTab ->
                    let
                        ( sm, sc ) =
                            Comp.SpaceManage.init flags
                    in
                    ( { m | spaceManageModel = sm }, Cmd.map SpaceMsg sc )

        TagManageMsg m ->
            let
                ( m2, c2 ) =
                    Comp.TagManage.update flags m model.tagManageModel
            in
            ( { model | tagManageModel = m2 }, Cmd.map TagManageMsg c2 )

        EquipManageMsg m ->
            let
                ( m2, c2 ) =
                    Comp.EquipmentManage.update flags m model.equipManageModel
            in
            ( { model | equipManageModel = m2 }, Cmd.map EquipManageMsg c2 )

        OrgManageMsg m ->
            let
                ( m2, c2 ) =
                    Comp.OrgManage.update flags m model.orgManageModel
            in
            ( { model | orgManageModel = m2 }, Cmd.map OrgManageMsg c2 )

        PersonManageMsg m ->
            let
                ( m2, c2 ) =
                    Comp.PersonManage.update flags m model.personManageModel
            in
            ( { model | personManageModel = m2 }, Cmd.map PersonManageMsg c2 )

        SpaceMsg lm ->
            let
                ( m2, c2 ) =
                    Comp.SpaceManage.update flags lm model.spaceManageModel
            in
            ( { model | spaceManageModel = m2 }
            , Cmd.map SpaceMsg c2
            )
